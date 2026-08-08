package com.freefcc.app

import android.content.SharedPreferences
import java.util.Locale

internal data class AircraftIdentityPreferenceUpdate(
    val previousModel: AircraftModelIdentity,
    val currentModel: AircraftModelIdentity,
    val previousSerial: String,
    val currentSerial: String,
    val modelChanged: Boolean,
    val serialChanged: Boolean
) {
    val changed: Boolean
        get() = modelChanged || serialChanged
}

/**
 * Decides when the passive `40007` window may be opened.
 *
 * An aircraft can be swapped while DJI Fly shows nothing but the FPV screen,
 * and that screen never prints a model name, so no screen hint ever arrives.
 * The Home Point text is read off that screen, and it is the one event that
 * proves an aircraft is really flying — it appears one to three minutes into a
 * session, by which time the bus is live and does carry the S/N. One window per
 * Home Point re-reads the identity, and it waits out a short guard first: the
 * same text starts the FCC write, and that write owns port `40007`.
 *
 * When nothing is known yet the window opens at once, then backs off. Holding
 * `40007` in a loop costs the DJI Fly link, so an aircraft that never publishes
 * its S/N must not be asked again and again.
 */
internal object AircraftIdentityProbePolicy {
    /**
     * How long identity stands aside after a Home Point so the FCC write it
     * triggers gets port `40007` first.
     *
     * The write finishes in a couple of seconds on real hardware, so this only
     * has to cover the gap between the Home Point being seen and the attempt
     * becoming visible as running: the Home Point is delivered through a
     * channel and the write starts on another thread, so it is not yet
     * registered at the instant identity checks. A write that takes longer, or
     * retries past this, is covered by the running-apply check in the service
     * rather than by making everyone wait here — thirty seconds of silence was
     * paid on every Home Point, and Home Point is a main identity trigger.
     */
    const val HOME_POINT_GUARD_MS = 5_000L
    const val UNKNOWN_RETRY_INTERVAL_MS = 5 * 60_000L

    /**
     * How often a complete identity is re-asked.
     *
     * Swapping to an aircraft of a *different* model is caught by the model
     * code changing, and a Home Point re-opens a window on its own. Neither
     * fires when the aircraft is replaced by another of the same model on a
     * session with no Home Point — indoors, or a flight that never got a fix —
     * and the previous aircraft's serial then stands in for it all session and
     * goes out in the statistics under the wrong aircraft.
     *
     * Verifying is cheap now that the serial can be asked for: one short
     * request, and no listening at all unless the answer differs from what we
     * hold. Discovery stays on the slower beat because it still needs the
     * listen that holds the port.
     */
    const val VERIFY_INTERVAL_MS = 2 * 60_000L

    fun shouldOpenWindow(
        storedSerial: String,
        homePointAtMs: Long,
        lastBusReadAtMs: Long,
        nowMs: Long,
        storedModelCode: String = ""
    ): Boolean {
        val homePointUnread = homePointAtMs > lastBusReadAtMs &&
            nowMs >= homePointAtMs + HOME_POINT_GUARD_MS
        if (homePointUnread) return true
        if (lastBusReadAtMs == 0L) return true
        // A stored identity no longer ends the beat. It used to, back when
        // reading meant listening and a known serial was worth keeping the
        // port free for. Now a complete identity is re-asked to catch a swap,
        // and an incomplete one keeps the slower discovery beat: the serial
        // arrives on the first window, but the model still has to be
        // overheard, so a missed model must not stay missing all session.
        val identityComplete = storedSerial.isNotEmpty() && storedModelCode.isNotEmpty()
        val interval = if (identityComplete) VERIFY_INTERVAL_MS else UNKNOWN_RETRY_INTERVAL_MS
        return nowMs - lastBusReadAtMs >= interval
    }
}

/** Applies one passive-link identity observation before statistics are scheduled. */
internal object AircraftIdentityPreferences {
    /**
     * DJI Fly prints editions the catalog does not carry, such as
     * `DJI Avata 360 Enhanced Transmission edition`. A screen name that adds
     * whole words to the observed one is that same aircraft spelled more
     * precisely. The added word must start a new word: model names differ by a
     * suffix — `DJI Air 3` and `DJI Air 3S`, `DJI Avata` and `DJI Avata 360` —
     * and reading those as one aircraft would hide a real swap.
     */
    private fun namesAgree(screenName: String, observedName: String): Boolean {
        val screen = screenName.uppercase(Locale.US)
        val observed = observedName.uppercase(Locale.US)
        return screen == observed || screen.startsWith("$observed ")
    }

    fun updateFromDuml(
        prefs: SharedPreferences,
        observedModel: AircraftModelIdentity?,
        observedSerial: String?,
        nowMs: Long
    ): AircraftIdentityPreferenceUpdate {
        val previousCode = prefs
            .getString(FccViewModel.PREF_AIRCRAFT_MODEL_CODE, "")
            .orEmpty()
        val previousName = prefs
            .getString(FccViewModel.PREF_AIRCRAFT_MODEL_NAME, "")
            .orEmpty()
        val previousSource = prefs
            .getString(FccViewModel.PREF_AIRCRAFT_MODEL_SOURCE, "")
            .orEmpty()
        val previousSerial = prefs.getString(AircraftSerialGuard.KEY_SERIAL, "").orEmpty()

        val observedCode = observedModel?.modelCode
            .orEmpty()
            .trim()
            .uppercase(Locale.US)
        val observedName = observedModel?.modelName
            .orEmpty()
            .trim()
            .ifEmpty { AircraftModelCatalog.nameForCode(observedCode) }
        val hasModelObservation = observedCode.isNotEmpty() || observedName.isNotEmpty()

        // The screen name outranks the bus name only while it can still belong
        // to the aircraft the bus reports. With no code stored, whatever names
        // the observed code — the bus itself, or the catalog when the bus sent
        // only a code — settles it: `DJI Lito X1` is not what a WA530 is
        // called, so a name the previous aircraft left behind does not survive.
        // A code nobody can name contradicts nothing, which keeps the verbatim
        // screen name of an aircraft DJI never listed.
        val screenNameFitsObservedCode = when {
            observedCode.isEmpty() || observedCode == previousCode -> true
            previousCode.isNotEmpty() -> false
            else -> observedName.isEmpty() || namesAgree(previousName, observedName)
        }
        val screenNameIsCurrent =
            previousSource == FccViewModel.AIRCRAFT_MODEL_SOURCE_UI &&
                previousName.isNotEmpty() &&
                screenNameFitsObservedCode
        // A serial that names a different aircraft proves the swap by itself.
        // The serial can now be asked for, so it arrives even when nothing
        // named the model: keeping the previous model beside it would file the
        // new aircraft under the old one, and — because a stored model stops
        // the next window from listening — that pairing would then never be
        // corrected. Dropping the model leaves the identity incomplete, which
        // is what puts the model back on the discovery beat.
        val acceptedSerialForSwap = observedSerial.orEmpty().trim()
        val confirmedSerialSwap = acceptedSerialForSwap.isNotEmpty() &&
            previousSerial.isNotEmpty() &&
            !AircraftSerialForms.sameAircraft(acceptedSerialForSwap, previousSerial)
        val modelOutlivedItsAircraft = confirmedSerialSwap && !hasModelObservation

        val currentCode = when {
            modelOutlivedItsAircraft -> ""
            else -> observedCode.ifEmpty { previousCode }
        }
        val currentName = when {
            modelOutlivedItsAircraft -> ""
            screenNameIsCurrent -> previousName
            observedName.isNotEmpty() -> observedName
            observedCode.isNotEmpty() && observedCode != previousCode -> ""
            else -> previousName
        }
        val modelChanged = (hasModelObservation || modelOutlivedItsAircraft) &&
            (currentCode != previousCode || currentName != previousName)
        val confirmedModelSwap = observedCode.isNotEmpty() && when {
            previousCode.isNotEmpty() -> observedCode != previousCode
            // No code was ever stored, so the contradicted screen name is the
            // only evidence of the swap — and it is enough to drop the S/N the
            // previous aircraft left behind.
            else -> previousName.isNotEmpty() && !screenNameFitsObservedCode
        }

        val acceptedSerial = observedSerial.orEmpty()
        val serialIsPreviousAircraft =
            AircraftSerialForms.sameAircraft(acceptedSerial, previousSerial)
        val currentSerial = when {
            // A code that proves a swap, next to the number of the aircraft
            // that just left — in either spelling — is the bus still repeating
            // the previous drone. Neither spelling may survive the swap.
            confirmedModelSwap && (acceptedSerial.isEmpty() || serialIsPreviousAircraft) -> ""
            acceptedSerial.isEmpty() -> previousSerial
            // The same aircraft spelled two ways is not a new aircraft: keep
            // the fuller spelling and leave everything else alone.
            serialIsPreviousAircraft ->
                AircraftSerialForms.preferred(previousSerial, acceptedSerial)
            else -> acceptedSerial
        }
        val serialChanged = currentSerial != previousSerial

        if (modelChanged || serialChanged) {
            prefs.edit().apply {
                if (modelChanged) {
                    if (currentCode.isEmpty()) {
                        remove(FccViewModel.PREF_AIRCRAFT_MODEL_CODE)
                    } else {
                        putString(FccViewModel.PREF_AIRCRAFT_MODEL_CODE, currentCode)
                    }
                    if (currentName.isEmpty()) {
                        remove(FccViewModel.PREF_AIRCRAFT_MODEL_NAME)
                    } else {
                        putString(FccViewModel.PREF_AIRCRAFT_MODEL_NAME, currentName)
                    }
                    putString(
                        FccViewModel.PREF_AIRCRAFT_MODEL_SOURCE,
                        FccViewModel.AIRCRAFT_MODEL_SOURCE_DUML
                    )
                    putLong(FccViewModel.PREF_AIRCRAFT_MODEL_AT, nowMs)
                }
                // A swap proven by the serial drops the previous one too, so
                // the frames the aircraft that just left keeps putting on the
                // bus cannot put it back.
                if ((confirmedModelSwap || confirmedSerialSwap) && previousSerial.isNotEmpty()) {
                    AircraftSerialGuard.rememberDropped(this, previousSerial, nowMs)
                }
                if (currentSerial.isNotEmpty()) {
                    putString(AircraftSerialGuard.KEY_SERIAL, currentSerial)
                }
            }.apply()
        }

        return AircraftIdentityPreferenceUpdate(
            previousModel = AircraftModelIdentity(previousCode, previousName),
            currentModel = AircraftModelIdentity(currentCode, currentName),
            previousSerial = previousSerial,
            currentSerial = currentSerial,
            modelChanged = modelChanged,
            serialChanged = serialChanged
        )
    }
}

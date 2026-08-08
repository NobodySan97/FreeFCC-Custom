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
 * Home Point re-reads the identity, and it waits out the guard first: the same
 * text starts the FCC write, and that write owns port `40007`.
 *
 * When nothing is known yet the window opens at once, then backs off. Holding
 * `40007` in a loop costs the DJI Fly link, so an aircraft that never publishes
 * its S/N must not be asked again and again.
 */
internal object AircraftIdentityProbePolicy {
    const val HOME_POINT_GUARD_MS = 30_000L
    const val UNKNOWN_RETRY_INTERVAL_MS = 5 * 60_000L

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
        // A serial no longer settles it. The serial can now be asked for
        // directly and arrives on the first window, while the model only comes
        // from listening and can be missed — and once the serial was stored,
        // this used to stop opening windows for good, leaving the aircraft
        // permanently unnamed on a session with no Home Point and no name on
        // the DJI Fly screen. Either half still missing keeps the slow beat.
        if (storedSerial.isNotEmpty() && storedModelCode.isNotEmpty()) return false
        if (lastBusReadAtMs == 0L) return true
        return nowMs - lastBusReadAtMs >= UNKNOWN_RETRY_INTERVAL_MS
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
        val currentCode = observedCode.ifEmpty { previousCode }
        val currentName = when {
            screenNameIsCurrent -> previousName
            observedName.isNotEmpty() -> observedName
            observedCode.isNotEmpty() && observedCode != previousCode -> ""
            else -> previousName
        }
        val modelChanged = hasModelObservation &&
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
                if (confirmedModelSwap && previousSerial.isNotEmpty()) {
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

package com.freefcc.app

import android.content.Context
import kotlinx.coroutines.delay

enum class GpsState {
    UNKNOWN,
    OFF,
    ON,
    UNEXPECTED
}

data class GpsReadback(
    val state: GpsState,
    val rawValue: Int
)

internal enum class GpsCommandSendResult {
    SENT,
    PORT_BUSY,
    TRANSPORT_FAILED
}

internal data class GpsFreshReadResult(
    val readback: GpsReadback?,
    val attemptedRead: Boolean
)

/** Shared bounded GPS wire operations used by the app UI, LAN and notification actions. */
internal object GpsCommandRunner {
    // Live rc331 evidence showed that one pair of cycles can flush all writes
    // while the verified aircraft state remains unchanged. Keep the operation
    // bounded, but include the equivalent of the second manual press.
    private const val COMMAND_CYCLES = 4
    private const val WRITES_PER_CYCLE = 5

    suspend fun send(
        enabled: Boolean,
        onProgress: (cycle: Int, cycles: Int, write: Int, writes: Int) -> Unit = { _, _, _, _ -> },
        onCycleRepeated: (cycle: Int, cycles: Int) -> Unit = { _, _ -> }
    ): GpsCommandSendResult {
        val portLease = Port40007Lock.acquireForLed() ?: return GpsCommandSendResult.PORT_BUSY
        return try {
            // Find out what this aircraft calls the parameter before writing to
            // it. Without this the first command of a session always addresses
            // the canonical name, so on a firmware that only knows the short
            // one every write in the burst lands nowhere and the switch looks
            // dead until something else happens to read it.
            if (!GpsControlProtocol.address.isConfirmed) readOnce()
            var anyWriteSucceeded = false
            for (cycle in 1..COMMAND_CYCLES) {
                for (write in 1..WRITES_PER_CYCLE) {
                    onProgress(cycle, COMMAND_CYCLES, write, WRITES_PER_CYCLE)
                    val request = GpsControlProtocol.buildWriteRequest(enabled)
                    val writeSucceeded = DumlTransport().sendFrame(
                        frame = Profiles.wrapFrame(request),
                        readWindowMs = 150,
                        port = DumlTransport.PORT_LED
                    )
                    anyWriteSucceeded = anyWriteSucceeded || writeSucceeded
                    if (write < WRITES_PER_CYCLE) delay(100)
                }
                if (cycle < COMMAND_CYCLES) {
                    onCycleRepeated(cycle, COMMAND_CYCLES)
                    delay(250)
                }
            }
            if (anyWriteSucceeded) {
                GpsCommandSendResult.SENT
            } else {
                GpsCommandSendResult.TRANSPORT_FAILED
            }
        } finally {
            Port40007Lock.releaseFromLed(portLease)
        }
    }

    suspend fun readFresh(
        onAttempt: (attempt: Int, attempts: Int) -> Unit = { _, _ -> },
        onMissing: (attempt: Int, attempts: Int) -> Unit = { _, _ -> }
    ): GpsFreshReadResult {
        var attemptedRead = false
        val attempts = 3
        for (attempt in 1..attempts) {
            onAttempt(attempt, attempts)
            val portLease = Port40007Lock.acquireForLed()
            val readback = if (portLease == null) {
                null
            } else {
                try {
                    attemptedRead = true
                    readOnce()
                } finally {
                    Port40007Lock.releaseFromLed(portLease)
                }
            }
            if (readback != null) return GpsFreshReadResult(readback, attemptedRead)
            onMissing(attempt, attempts)
            if (attempt < attempts) delay(150)
        }
        return GpsFreshReadResult(null, attemptedRead)
    }

    private fun readOnce(): GpsReadback? {
        val transport = DumlTransport()
        fun ask(hashes: List<ByteArray>) = ParameterAddress.read(
            transport = transport,
            address = GpsControlProtocol.address,
            readWindowMs = 2_500,
            buildRequest = { hash -> GpsControlProtocol.buildReadRequest(hash) },
            parse = GpsControlProtocol::parse,
            hashes = hashes
        )
        return ask(listOf(GpsControlProtocol.address.preferred()))
            ?: ask(GpsControlProtocol.address.alternates())
    }
}

internal object GpsControlStateStore {
    private const val PREFS_NAME = "freefcc"
    internal const val PREF_STATE = "gps_last_verified_state"
    internal const val PREF_RAW = "gps_last_verified_raw"
    internal const val PREF_AT = "gps_last_verified_at"

    fun load(context: Context): Pair<GpsReadback, Long>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(PREF_STATE) || !prefs.contains(PREF_RAW)) return null
        val state = runCatching {
            GpsState.valueOf(prefs.getString(PREF_STATE, null).orEmpty())
        }.getOrNull() ?: return null
        return GpsReadback(state, prefs.getInt(PREF_RAW, 0)) to prefs.getLong(PREF_AT, 0L)
    }

    fun persist(context: Context, readback: GpsReadback, verifiedAtMs: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_STATE, readback.state.name)
            .putInt(PREF_RAW, readback.rawValue)
            .putLong(PREF_AT, verifiedAtMs)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(PREF_STATE)
            .remove(PREF_RAW)
            .remove(PREF_AT)
            .apply()
    }
}

/** Hash-based access to the aircraft master `g_config.gps_cfg.gps_enable` parameter. */
internal object GpsControlProtocol {
    // Read-only 03:F8 was verified live through the RC2 port-40007 proxy.
    // Which spelling of the name a firmware carries decides the hash, so the
    // parameter is addressed by candidate rather than by one constant.
    val address: ParameterAddress = ParameterAddress.GPS_ENABLE

    fun buildReadRequest(
        parameterHash: ByteArray = address.preferred(),
        builder: DumlBuilder = DumlBuilder()
    ): ByteArray = buildRequest(builder, commandId = 0xF8, value = null, parameterHash = parameterHash)

    fun buildWriteRequest(enabled: Boolean, builder: DumlBuilder = DumlBuilder()): ByteArray =
        buildRequest(
            builder,
            commandId = 0xF9,
            value = if (enabled) 1 else 0,
            // Write to the name a readback proved this aircraft answers to.
            parameterHash = address.preferred()
        )

    fun parse(payload: ByteArray?): GpsReadback? {
        if (payload == null || payload.size != 6 || payload[0] != 0.toByte()) return null
        val echoed = payload.copyOfRange(1, 5)
        if (!address.matches(echoed)) return null
        address.confirm(echoed)

        val value = payload[5].toInt() and 0xFF
        val state = when (value) {
            0 -> GpsState.OFF
            1 -> GpsState.ON
            else -> GpsState.UNEXPECTED
        }
        return GpsReadback(state, value)
    }

    private fun buildRequest(
        builder: DumlBuilder,
        commandId: Int,
        value: Int?,
        parameterHash: ByteArray
    ): ByteArray {
        val payload = if (value == null) {
            parameterHash.copyOf()
        } else {
            parameterHash + value.toByte()
        }
        return builder.buildFrame(
            DumlFrame(
                sender = 0x02,
                cmdType = 0x40,
                cmdSet = 0x03,
                cmdId = commandId,
                dst = 0x03,
                payload = payload
            )
        )
    }
}

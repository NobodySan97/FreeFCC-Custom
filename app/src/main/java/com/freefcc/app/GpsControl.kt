package com.freefcc.app

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

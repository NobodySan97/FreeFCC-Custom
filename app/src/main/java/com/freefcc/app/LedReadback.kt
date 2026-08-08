package com.freefcc.app

enum class LedState {
    UNKNOWN,
    OFF,
    ON,
    PARTIAL
}

data class LedReadback(
    val state: LedState,
    val rawValue: Int
)

internal object LedReadbackProtocol {

    /** Hashes to try, canonical name first. See [ParameterAddress]. */
    val address: ParameterAddress = ParameterAddress.FOREARM_LED

    fun buildRequest(
        parameterHash: ByteArray = address.preferred(),
        builder: DumlBuilder = DumlBuilder()
    ): ByteArray =
        builder.buildFrame(
            DumlFrame(
                sender = 0x02,
                cmdType = 0x40,
                cmdSet = 0x03,
                cmdId = 0xF8,
                dst = 0x03,
                payload = parameterHash
            )
        )

    /**
     * Accepts a reply for any spelling of the parameter and remembers which one
     * answered, so the following write addresses the same name.
     */
    fun parse(payload: ByteArray?): LedReadback? {
        if (payload == null || payload.size != 6 || payload[0] != 0.toByte()) return null
        val echoed = payload.copyOfRange(1, 5)
        if (!address.matches(echoed)) return null
        address.confirm(echoed)

        val value = payload[5].toInt() and 0xFF
        val state = when (value) {
            0x00 -> LedState.OFF
            0xEF -> LedState.ON
            else -> LedState.PARTIAL
        }
        return LedReadback(state, value)
    }
}

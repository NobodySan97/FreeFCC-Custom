package com.freefcc.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AircraftModelIdentityTest {

    @Test
    fun extractsLiveAir3sCodeAndNameFrames() {
        val identity = DumlTransport.extractAircraftModelIdentity(
            listOf(
                hexToBytes(
                    "554d04a8a282df98000082574132333400000000000000000000000000000000" +
                        "0000000000000000000000020800000000000002080000020000000000000000" +
                        "0000000000000000000000fe8c"
                ),
                hexToBytes(
                    "552e04a7a2829f9800033400444a492041697220335300000000000000000000" +
                        "000000000000000000000000d085"
                )
            )
        )

        assertEquals("WA234", identity?.modelCode)
        assertEquals("DJI Air 3S", identity?.modelName)
    }

    @Test
    fun ignoresControllerIdentityInSameCommand() {
        val identity = DumlTransport.extractAircraftModelIdentity(
            listOf(
                hexToBytes(
                    "554d04a80d2aad0a400082726335323000000000000000000000000000000000" +
                        "00000000000000000000000d000000000000000d000002000000000000000000" +
                        "000000000000000000006f49"
                )
            )
        )

        assertNull(identity)
    }

    @Test
    fun extractsLiveMavic3tEnterpriseCodeWithoutPilot2() {
        val identity = DumlTransport.extractAircraftModelIdentity(
            listOf(
                hexToBytes(
                    "554d04a8a2823876000082574d32363554000000000000000000000000000000" +
                        "0000000000000000000000020800000000000002080000000000000000000000" +
                        "00000000000000000000009714"
                )
            )
        )

        assertEquals("WM265T", identity?.modelCode)
        assertEquals("DJI Mavic 3T", identity?.modelName)
    }

    @Test
    fun acceptsUnknownAircraftProductCodes() {
        listOf("PM430", "AG410").forEach { productCode ->
            val identity = DumlTransport.extractAircraftModelIdentity(
                listOf(modelCodeFrame(productCode))
            )

            assertEquals(productCode, identity?.modelCode)
            assertEquals("", identity?.modelName)
        }
    }

    @Test
    fun namesCatalogedCodesWithoutTheUserStringFrame() {
        val identity = DumlTransport.extractAircraftModelIdentity(
            listOf(modelCodeFrame("WM2605"))
        )

        assertEquals("WM2605", identity?.modelCode)
        assertEquals("DJI Mavic 3 Classic", identity?.modelName)
    }

    @Test
    fun rejectsControllerAndNonProductCodes() {
        listOf("RC520", "RM510", "GL300", "DJI", "123").forEach { productCode ->
            val identity = DumlTransport.extractAircraftModelIdentity(
                listOf(modelCodeFrame(productCode))
            )

            assertNull(productCode, identity)
        }
    }

    private fun modelCodeFrame(productCode: String): ByteArray =
        DumlBuilder().buildFrame(
            DumlFrame(
                sender = 0xA2,
                dst = 0x82,
                cmdType = 0x80,
                cmdSet = 0x00,
                cmdId = 0x82,
                payload = productCode.toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
            )
        )

    private fun hexToBytes(value: String): ByteArray =
        ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
}

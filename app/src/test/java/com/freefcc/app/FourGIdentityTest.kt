package com.freefcc.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FourGIdentityTest {

    @Test
    fun fullFactorySerialIsAccepted() {
        val serial = "1581ABCDEF012345"
        val identity = FccViewModel.parseFourGIdentity(serial)

        assertEquals(serial, identity?.payloadSerial)
        assertNull(identity?.modelCode)
    }

    @Test
    fun fullFactorySerialIsNormalized() {
        val identity = FccViewModel.parseFourGIdentity("  1581abcdef012345 ")

        assertEquals("1581ABCDEF012345", identity?.payloadSerial)
    }

    @Test
    fun shortModelCodeIsRejected() {
        // A model code is not a peer SN: the WLM resolves the payload's SN
        // field via wlm_peer_dev_list_find, so WA341 would be refused.
        assertNull(FccViewModel.parseFourGIdentity("WA341"))
        assertNull(FccViewModel.parseFourGIdentity("WA530"))
        assertNull(FccViewModel.parseFourGIdentity("WM265T"))
        assertNull(FccViewModel.parseFourGIdentity("wa341test"))
    }

    @Test
    fun malformedIdentityIsRejected() {
        assertNull(FccViewModel.parseFourGIdentity(""))
        assertNull(FccViewModel.parseFourGIdentity("1581TOO_SHORT"))
        assertNull(FccViewModel.parseFourGIdentity("not-a-dji-identity"))
    }
}

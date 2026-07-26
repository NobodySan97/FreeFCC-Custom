package com.freefcc.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AircraftModelCatalogTest {

    @Test
    fun namesKnownProductCodes() {
        assertEquals("DJI Air 3S", AircraftModelCatalog.nameForCode("WA234"))
        assertEquals("DJI Mavic 4 Pro", AircraftModelCatalog.nameForCode("WA341"))
        assertEquals("DJI Mavic 3 Classic", AircraftModelCatalog.nameForCode("WM2605"))
        assertEquals("DJI Mavic 3T", AircraftModelCatalog.nameForCode("wm265t"))
        assertEquals("", AircraftModelCatalog.nameForCode("WA999"))
    }

    @Test
    fun readsCodeAndNameOffTheSameScreen() {
        val match = AircraftModelCatalog.findInText(
            "DJI Air 3S | WA234 | Battery 87% | Home Point updated"
        )

        assertEquals("WA234", match?.code)
        assertEquals("DJI Air 3S", match?.name)
    }

    @Test
    fun prefersTheLongerNameOverItsPrefix() {
        assertEquals("DJI Air 3S", AircraftModelCatalog.findInText("DJI Air 3S")?.name)
        assertEquals("WA234", AircraftModelCatalog.findInText("DJI Air 3S")?.code)
        assertEquals("DJI Air 3", AircraftModelCatalog.findInText("DJI Air 3")?.name)
        assertEquals("WA233", AircraftModelCatalog.findInText("DJI Air 3")?.code)
        assertEquals("DJI Mavic 3T", AircraftModelCatalog.findInText("Mavic 3T")?.name)
        assertEquals("DJI Mavic 3", AircraftModelCatalog.findInText("DJI Mavic 3")?.name)
    }

    @Test
    fun infersTheCodeFromAnUnambiguousName() {
        val match = AircraftModelCatalog.findInText("Connected to DJI Mini 4 Pro")

        assertEquals("WA140", match?.code)
        assertEquals("DJI Mini 4 Pro", match?.name)
    }

    @Test
    fun refusesToGuessACodeForTheSharedMavic2Name() {
        val match = AircraftModelCatalog.findInText("DJI Mavic 2")

        assertEquals("", match?.code)
        assertEquals("DJI Mavic 2", match?.name)
    }

    @Test
    fun namesACodeThatAppearsWithoutItsCommercialName() {
        val match = AircraftModelCatalog.findInText("Aircraft WA341 linked")

        assertEquals("WA341", match?.code)
        assertEquals("DJI Mavic 4 Pro", match?.name)
    }

    @Test
    fun ignoresScreensWithoutAnAircraftIdentity() {
        listOf(
            "Home Point updated | 12.5 m/s | 120 m",
            "NEON | FLIPPED | AIRPLANE MODE",
            "RC520 | rm510 | Battery 64%",
            ""
        ).forEach { screen ->
            assertNull(screen, AircraftModelCatalog.findInText(screen))
        }
    }
}

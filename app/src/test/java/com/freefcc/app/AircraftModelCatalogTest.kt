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
    fun keepsTheNameTheScreenPrintsEvenWhenItIsUnknown() {
        val match = AircraftModelCatalog.findOnScreen(
            listOf("DJI Avata 360", "Battery 87%", "Home Point updated")
        )

        assertEquals("DJI Avata 360", match?.name)
        assertEquals("", match?.code)
    }

    @Test
    fun readsCodeAndNameOffTheSameScreen() {
        val match = AircraftModelCatalog.findOnScreen(
            listOf("DJI Avata 360", "WA530", "Battery 87%")
        )

        assertEquals("DJI Avata 360", match?.name)
        assertEquals("WA530", match?.code)
    }

    @Test
    fun readsANameThatSharedItsLabelWithTheCode() {
        val match = AircraftModelCatalog.findOnScreen(listOf("Aircraft: DJI Avata 360 (WA530)"))

        assertEquals("DJI Avata 360", match?.name)
        assertEquals("WA530", match?.code)
    }

    @Test
    fun infersTheCodeFromAnUnambiguousName() {
        val match = AircraftModelCatalog.findOnScreen(listOf("DJI Mini 4 Pro"))

        assertEquals("WA140", match?.code)
        assertEquals("DJI Mini 4 Pro", match?.name)
    }

    @Test
    fun prefersTheLongerNameOverItsPrefix() {
        assertEquals("WA234", AircraftModelCatalog.findOnScreen(listOf("DJI Air 3S"))?.code)
        assertEquals("WA233", AircraftModelCatalog.findOnScreen(listOf("DJI Air 3"))?.code)
        assertEquals(
            "DJI Mavic 3T",
            AircraftModelCatalog.findOnScreen(listOf("Connected to Mavic 3T"))?.name
        )
    }

    @Test
    fun refusesToGuessACodeForTheSharedMavic2Name() {
        val match = AircraftModelCatalog.findOnScreen(listOf("DJI Mavic 2"))

        assertEquals("", match?.code)
        assertEquals("DJI Mavic 2", match?.name)
    }

    @Test
    fun namesACodeThatAppearsWithoutItsCommercialName() {
        val match = AircraftModelCatalog.findOnScreen(listOf("Aircraft", "WA341", "linked"))

        assertEquals("WA341", match?.code)
        assertEquals("DJI Mavic 4 Pro", match?.name)
    }

    @Test
    fun ignoresDjiProductsThatAreNotTheAircraft() {
        listOf(
            "DJI Fly",
            "DJI Care Refresh",
            "DJI Store",
            "DJI RC Pro 2",
            "DJI Goggles 3"
        ).forEach { label ->
            assertNull(label, AircraftModelCatalog.findOnScreen(listOf(label, "Battery 87%")))
        }
    }

    @Test
    fun readsAnUnknownNameOutOfASentence() {
        val match = AircraftModelCatalog.findOnScreen(
            listOf("Подключено: DJI Avata 360", "Battery 87%")
        )

        assertEquals("DJI Avata 360", match?.name)
        assertEquals("", match?.code)
    }

    @Test
    fun neverShortensAKnownNameThatContinuesWithANumber() {
        val match = AircraftModelCatalog.findOnScreen(listOf("Connected to DJI Avata 360"))

        assertEquals("DJI Avata 360", match?.name)
        assertEquals("", match?.code)
    }

    @Test
    fun dropsAStoredCodeThatBelongsToAnotherAircraft() {
        // Avata 360 named on screen, WM169 (DJI Avata) left over from before.
        assertEquals(
            "",
            AircraftModelCatalog.codeFor("DJI Avata 360", "", "DJI Avata 360", "WM169")
        )
        assertEquals("", AircraftModelCatalog.codeFor("DJI Air 3S", "", "DJI Avata", "WM169"))
    }

    @Test
    fun keepsAStoredCodeThatMatchesTheName() {
        assertEquals(
            "WM169",
            AircraftModelCatalog.codeFor("DJI Avata", "", "DJI Avata", "WM169")
        )
        assertEquals(
            "WA530",
            AircraftModelCatalog.codeFor("DJI Avata 360", "", "DJI Avata 360", "WA530")
        )
        assertEquals(
            "WA234",
            AircraftModelCatalog.codeFor("DJI Air 3S", "WA234", "DJI Avata", "WM169")
        )
    }

    @Test
    fun ignoresScreensWithoutAnAircraftIdentity() {
        listOf(
            listOf("Home Point updated", "12.5 m/s", "120 m"),
            listOf("NEON", "FLIPPED", "AIRPLANE MODE"),
            listOf("RC520", "rm510", "Battery 64%"),
            listOf("")
        ).forEach { screen ->
            assertNull(screen.toString(), AircraftModelCatalog.findOnScreen(screen))
        }
    }
}

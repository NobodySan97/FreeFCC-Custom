package com.freefcc.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatisticsTest {
    @Test
    fun extractsRemoteControllerSerialFromAdjacentDjiFlyLabel() {
        assertEquals(
            "5WTBH123456789",
            DjiFlyControllerSerialExtractor.find(
                listOf("About", "Remote Controller S/N", "5WTBH123456789", "Firmware 01.00")
            )
        )
    }

    @Test
    fun extractsRemoteControllerSerialFromRussianInlineLabel() {
        assertEquals(
            "6AXCH987654321",
            DjiFlyControllerSerialExtractor.find(
                listOf("Серийный номер пульта: 6AXCH987654321")
            )
        )
    }

    @Test
    fun extractsControllerSerialFromObservedRc2AvataInformationLayout() {
        val labels = listOf(
            "Название Wi-Fi",
            "DJI-AVATA360-TEST",
            "Модель",
            "DJI Avata 360",
            "Версия приложения",
            "1.21.8",
            "Прошивка дрона",
            "01.00.0500",
            "Серийный номер дрона",
            "1581FAKE000000000001",
            "Серийный номер полетного контроллера",
            "Серийный номер камеры",
            "9VMFAKE000001",
            "Серийный номер батареи",
            "A4SPFAKE000001",
            "Серийный номер пульта",
            "6UZBFAKE000001"
        )

        assertEquals("6UZBFAKE000001", DjiFlyControllerSerialExtractor.find(labels))
    }

    @Test
    fun extractsControllerSerialFromEnglishInformationLayout() {
        val labels = listOf(
            "Model",
            "DJI Avata 360",
            "App Version",
            "1.21.8",
            "Aircraft Serial Number",
            "1581FAKE000000000001",
            "Flight Controller Serial Number",
            "Camera Serial Number",
            "9VMFAKE000001",
            "Battery Serial Number",
            "A4SPFAKE000001",
            "Remote Controller S/N",
            "6UZBFAKE000001"
        )

        assertEquals("6UZBFAKE000001", DjiFlyControllerSerialExtractor.find(labels))
    }

    @Test
    fun extractsAircraftSerialFromObservedLitoX1InformationLayout() {
        val labels = listOf(
            "Проверить наличие обновлений",
            "SN",
            "Серийный номер дрона",
            "1581FB34C25CF0032AAG",
            "Серийный номер полетного контроллера",
            "Серийный номер камеры",
            "AVYFN9T0M11CY2",
            "Серийный номер батареи",
            "B38PNCDAA001PE",
            "Серийный номер пульта",
            "6UZBL7302102G9"
        )

        assertEquals("1581FB34C25CF0032AAG", DjiFlyAircraftSerialExtractor.find(labels))
    }

    @Test
    fun extractsAircraftSerialFromEnglishInformationLayout() {
        val labels = listOf(
            "Model",
            "DJI Lito X1",
            "Aircraft Serial Number",
            "1581FB34C25CF0032AAG",
            "Flight Controller Serial Number",
            "Camera Serial Number",
            "AVYFN9T0M11CY2",
            "Remote Controller S/N",
            "6UZBL7302102G9"
        )

        assertEquals("1581FB34C25CF0032AAG", DjiFlyAircraftSerialExtractor.find(labels))
    }

    @Test
    fun aircraftSerialIsNotTakenFromAnotherComponentWithABlankValue() {
        val labels = listOf(
            "Серийный номер дрона",
            "Серийный номер камеры",
            "AVYFN9T0M11CY2"
        )

        assertNull(DjiFlyAircraftSerialExtractor.find(labels))
    }

    @Test
    fun aircraftSerialIgnoresControllerAndModelCodeLabels() {
        assertNull(
            DjiFlyAircraftSerialExtractor.find(
                listOf("Серийный номер пульта", "6UZBL7302102G9")
            )
        )
        assertNull(
            DjiFlyAircraftSerialExtractor.find(
                listOf("Aircraft Serial Number", "WA341")
            )
        )
    }

    @Test
    fun extractsAircraftSerialFromInlineLabel() {
        assertEquals(
            "1581FB34C25CF0032AAG",
            DjiFlyAircraftSerialExtractor.find(
                listOf("Серийный номер дрона: 1581FB34C25CF0032AAG")
            )
        )
    }

    @Test
    fun serialGuardRefusesTheSerialOfTheAircraftJustUnplugged() {
        val dropped = "1581F9DEC25AQ02998T5"

        // Live case: a Lito X1 was plugged in and the bus still repeated the
        // Mini 5 Pro serial six seconds later.
        assertFalse(
            AircraftSerialGuard.accepts(
                dropped = dropped,
                droppedAtMs = 1_000L,
                serial = dropped,
                nowMs = 7_000L
            )
        )
        // Another aircraft's serial is never held back.
        assertTrue(
            AircraftSerialGuard.accepts(
                dropped = dropped,
                droppedAtMs = 1_000L,
                serial = "1581FB34C25CF0032AAG",
                nowMs = 7_000L
            )
        )
        // The same aircraft coming back is read again once the window passes.
        assertTrue(
            AircraftSerialGuard.accepts(
                dropped = dropped,
                droppedAtMs = 1_000L,
                serial = dropped,
                nowMs = 1_000L + AircraftSerialGuard.GUARD_MS
            )
        )
        assertTrue(
            AircraftSerialGuard.accepts(
                dropped = "",
                droppedAtMs = 0L,
                serial = dropped,
                nowMs = 7_000L
            )
        )
        // The dropped aircraft also comes back spelled without its `1581`
        // prefix — `03:44` publishes only the tail — and is still refused.
        assertFalse(
            AircraftSerialGuard.accepts(
                dropped = dropped,
                droppedAtMs = 1_000L,
                serial = dropped.removePrefix("1581"),
                nowMs = 7_000L
            )
        )
    }

    @Test
    fun automaticSerialReaderSkipsAndroidPlaceholders() {
        assertEquals(
            ControllerSerialObservation("6UZBFAKE000001", "getprop_ro_serialno"),
            AutomaticControllerSerialReader.firstValid(
                listOf(
                    "unknown" to "build_serial",
                    "0123456789abcdef" to "getprop_ro_boot_serialno",
                    "0000000000000000" to "getprop_ro_boot_serialno",
                    "6uzbfake000001" to "getprop_ro_serialno"
                )
            )
        )
        assertNull(
            AutomaticControllerSerialReader.firstValid(
                listOf("rc331" to "getprop_ro_serialno", "aaaaaaaaaaaa" to "usb_gadget")
            )
        )
    }

    @Test
    fun automaticControllerSerialIsProbedOnlyOnce() {
        assertTrue(UsageStatistics.shouldProbeControllerSerial("", probeDone = false))
        assertFalse(UsageStatistics.shouldProbeControllerSerial("", probeDone = true))
        assertFalse(
            UsageStatistics.shouldProbeControllerSerial("6UZBFAKE000001", probeDone = false)
        )
    }

    @Test
    fun doesNotConfuseAircraftComponentsWithRemoteController() {
        assertEquals(
            "7RCBH123456789",
            DjiFlyControllerSerialExtractor.find(
                listOf(
                    "Flight Controller SN",
                    "1581F4QWD123456789",
                    "Remote Controller Serial Number",
                    "7RCBH123456789"
                )
            )
        )
        assertNull(
            DjiFlyControllerSerialExtractor.find(
                listOf("Aircraft S/N", "1581F4QWD123456789", "Battery S/N", "4ER123456789")
            )
        )
    }

    @Test
    fun groupsAbsoluteCountersByAppVersion() {
        val counters = UsageStatistics.usageCounters(
            mapOf(
                UsageStatistics.counterKey("1.5.60", "gps_on") to 4L,
                UsageStatistics.counterKey("1.5.60", "gps_off") to 3,
                UsageStatistics.counterKey("1.6.0-beta.1", "manual_fcc") to 2L,
                "unrelated" to 99L,
                "count.broken" to -1L
            )
        )

        assertEquals(mapOf("gps_off" to 3L, "gps_on" to 4L), counters["1.5.60"])
        assertEquals(mapOf("manual_fcc" to 2L), counters["1.6.0-beta.1"])
        assertEquals(2, counters.size)
    }

    @Test
    fun uploadPolicyIsDailyWithHourlyFailureRetryAndForcedIdentityUpload() {
        val hour = 60 * 60 * 1000L
        val day = 24 * hour
        val now = 2 * day

        assertFalse(UsageStatistics.shouldUpload(now, now - hour, 0L, force = false))
        assertFalse(UsageStatistics.shouldUpload(now, 0L, now - hour / 2, force = false))
        assertTrue(UsageStatistics.shouldUpload(now, 0L, now - hour, force = false))
        assertTrue(UsageStatistics.shouldUpload(now, now - 1L, now - 1L, force = true))
        assertTrue(UsageStatistics.shouldUpload(now, now + hour, now + hour, force = false))
    }

    @Test
    fun bothEndpointsReceiveTheSameReportAndOnlyValidOnesAreKept() {
        assertEquals(
            listOf("https://one.example/api/v1/statistics", "https://two.example/api/v1/statistics"),
            UsageStatistics.endpoints(
                " https://one.example/api/v1/statistics ",
                "https://two.example/api/v1/statistics"
            )
        )
        // Одинаковые адреса — один сервер, отчёт не должен уйти туда дважды.
        assertEquals(
            listOf("https://one.example/api/v1/statistics"),
            UsageStatistics.endpoints(
                "https://one.example/api/v1/statistics",
                "https://one.example/api/v1/statistics"
            )
        )
        // Незаданный резерв оставляет ровно один приёмник.
        assertEquals(
            listOf("https://one.example/api/v1/statistics"),
            UsageStatistics.endpoints("https://one.example/api/v1/statistics", "")
        )
        // Без HTTPS отправки нет вовсе.
        assertEquals(emptyList<String>(), UsageStatistics.endpoints("", ""))
        assertEquals(
            emptyList<String>(),
            UsageStatistics.endpoints("http://one.example/api/v1/statistics", "  ")
        )
    }

    @Test
    fun payloadContainsDeclaredStatisticsOnly() {
        val json = UsageStatisticsJson.encode(
            UsageStatisticsPayload(
                installationId = "439e9436-d52c-4a43-a49c-645d3fb1cc73",
                reportSequence = 7,
                appVersionName = "1.5.60",
                appVersionCode = 77,
                controllerSerial = "5WTBH123456789",
                controllerSerialSource = "getprop_ro_serialno",
                controllerDevice = "rc331",
                controllerModel = "DJI RC 2",
                djiFlyVersionName = "1.21.4",
                djiFlyVersionCode = 1021040,
                aircraftSerial = "1581F6ABCDEF1234",
                aircraftModelCode = "WA530",
                aircraftModelName = "DJI Avata 360",
                settings = mapOf("auto_fcc_mode" to "home_point_text"),
                usageByAppVersion = mapOf("1.5.60" to mapOf("gps_off" to 3L))
            )
        )

        assertTrue(json.contains("\"controller_serial\":\"5WTBH123456789\""))
        assertTrue(json.contains("\"controller_serial_source\":\"getprop_ro_serialno\""))
        assertTrue(json.contains("\"schema_version\":2"))
        assertTrue(json.contains("\"aircraft_serial\":\"1581F6ABCDEF1234\""))
        assertTrue(json.contains("\"gps_off\":3"))
        assertFalse(json.contains("coordinate"))
        assertFalse(json.contains("logcat"))
    }
}

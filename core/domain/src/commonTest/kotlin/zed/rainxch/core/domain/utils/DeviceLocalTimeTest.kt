package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceLocalTimeTest {

    @Test
    fun utc_plus_eight_moves_date_across_midnight() {
        val converted =
            DeviceLocalTime.toDeviceLocal(
                isoTimestamp = "2026-08-21T18:49:24Z",
                timeZone = DeviceLocalTime.utcOffsetHours(8),
            )

        assertEquals("2026-08-21T18:49:24Z", converted?.utc)
        assertEquals("2026-08-22 02:49:24", converted?.localDateTime)
        assertEquals("2026-08-22", DeviceLocalTime.toDeviceLocalDate(
            isoTimestamp = "2026-08-21T18:49:24Z",
            timeZone = DeviceLocalTime.utcOffsetHours(8),
        ))
    }

    @Test
    fun utc_keeps_original_calendar_date() {
        val converted =
            DeviceLocalTime.toDeviceLocal(
                isoTimestamp = "2026-08-21T18:49:24Z",
                timeZone = DeviceLocalTime.utcOffsetHours(0),
            )

        assertEquals("2026-08-21 18:49:24", converted?.localDateTime)
        assertEquals("2026-08-21", DeviceLocalTime.toDeviceLocalDate(
            isoTimestamp = "2026-08-21T18:49:24Z",
            timeZone = DeviceLocalTime.utcOffsetHours(0),
        ))
    }

    @Test
    fun invalid_or_blank_timestamp_returns_null() {
        assertNull(DeviceLocalTime.toDeviceLocal(null, DeviceLocalTime.utcOffsetHours(8)))
        assertNull(DeviceLocalTime.toDeviceLocal("", DeviceLocalTime.utcOffsetHours(8)))
        assertNull(DeviceLocalTime.toDeviceLocal("not-a-date", DeviceLocalTime.utcOffsetHours(8)))
        assertNull(DeviceLocalTime.parseIsoInstant("2026-08-21 18:49:24"))
    }
}

package zed.rainxch.core.domain.utils

import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class DeviceLocalInstant(
    val utc: String,
    val localDateTime: String,
    val timeZoneId: String,
)

object DeviceLocalTime {
    private val localDateTimeFormat =
        LocalDateTime.Format {
            year()
            char('-')
            monthNumber()
            char('-')
            day()
            char(' ')
            hour()
            char(':')
            minute()
            char(':')
            second()
        }

    @OptIn(ExperimentalTime::class)
    fun parseIsoInstant(isoTimestamp: String?): Instant? {
        val trimmed = isoTimestamp?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return runCatching { Instant.parse(trimmed) }.getOrNull()
    }

    @OptIn(ExperimentalTime::class)
    fun toDeviceLocal(
        isoTimestamp: String?,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): DeviceLocalInstant? {
        val instant = parseIsoInstant(isoTimestamp) ?: return null
        return DeviceLocalInstant(
            utc = instant.toString(),
            localDateTime = instant.toLocalDateTime(timeZone).format(localDateTimeFormat),
            timeZoneId = timeZone.id,
        )
    }

    @OptIn(ExperimentalTime::class)
    fun toDeviceLocalDate(
        isoTimestamp: String?,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): String? {
        val instant = parseIsoInstant(isoTimestamp) ?: return null
        return instant.toLocalDateTime(timeZone).date.toString()
    }

    fun utcOffsetHours(hours: Int): TimeZone =
        FixedOffsetTimeZone(UtcOffset(hours = hours))
}

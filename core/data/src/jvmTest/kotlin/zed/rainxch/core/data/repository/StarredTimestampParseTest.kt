package zed.rainxch.core.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `starred_at` is external input and the field only became populated once the media type was
 * fixed. The parse runs inside the `async` that builds a row, so an unguarded throw would
 * cancel the enclosing scope and fail the whole sync - everything or nothing, over one bad
 * timestamp.
 */
class StarredTimestampParseTest {
    @Test
    fun `an iso timestamp parses to epoch milliseconds`() {
        assertEquals(1_000L, "1970-01-01T00:00:01Z".toEpochMillisecondsOrNull())
    }

    @Test
    fun `a missing value stays null`() {
        assertNull((null as String?).toEpochMillisecondsOrNull())
    }

    @Test
    fun `an unparsable value degrades to null instead of throwing`() {
        assertNull("not-a-timestamp".toEpochMillisecondsOrNull())
        assertNull("".toEpochMillisecondsOrNull())
    }
}

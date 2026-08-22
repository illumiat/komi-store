package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckReportTest {

    @Test
    fun timestamp_miss_answers_whether_logic_ran_and_why() {
        val report =
            UpdateCheckReport(
                usedTimestampLogic = true,
                branch = "timestamp",
                reason = "timestamp_not_newer",
                windowSource = "github",
                windowSize = 1,
                includePreReleases = true,
                opaqueMatched = true,
                sameTag = false,
                reconcilable = false,
                installedTag = "0.1.0",
                matchedTag = "nightly",
                storedPublishedAt = "2026-08-19T16:08:17Z",
                matchedPublishedAt = "2026-08-19T16:08:17Z",
                codesAlreadyMatch = false,
                isUpdate = false,
                timeZone = DeviceLocalTime.utcOffsetHours(8),
            ).format()

        assertContains(report, "usedTimestampLogic=true")
        assertContains(report, "branch=timestamp")
        assertContains(report, "reason=timestamp_not_newer")
        assertContains(report, "matchedTag=nightly")
        assertContains(report, "storedPublishedAt=2026-08-19T16:08:17Z")
        assertContains(report, "storedPublishedAtLocal=2026-08-20 00:08:17")
        assertContains(report, "matchedPublishedAt=2026-08-19T16:08:17Z")
        assertContains(report, "matchedPublishedAtLocal=2026-08-20 00:08:17")
        assertContains(report, "deviceTimeZone=")
        assertContains(report, "isUpdate=false")
        assertTrue(report.startsWith("usedTimestampLogic="))
    }

    @Test
    fun disabled_check_does_not_claim_timestamp_logic() {
        val report =
            UpdateCheckReport(
                usedTimestampLogic = false,
                branch = "disabled",
                reason = "update_check_disabled",
                windowSource = "skipped",
                windowSize = 0,
                includePreReleases = true,
                opaqueMatched = false,
                sameTag = false,
                reconcilable = false,
                installedTag = "nightly",
                matchedTag = null,
                storedPublishedAt = null,
                matchedPublishedAt = null,
                codesAlreadyMatch = false,
                isUpdate = false,
            ).format()

        assertContains(report, "usedTimestampLogic=false")
        assertContains(report, "branch=disabled")
        assertContains(report, "matchedTag=null")
        assertFalse(report.contains("usedTimestampLogic=true"))
    }

    @Test
    fun empty_window_reports_source_and_size() {
        val report =
            UpdateCheckReport(
                usedTimestampLogic = false,
                branch = "empty_window",
                reason = "no_releases_in_window",
                windowSource = "backend_blocked",
                windowSize = 0,
                includePreReleases = true,
                opaqueMatched = false,
                sameTag = false,
                reconcilable = false,
                installedTag = "nightly",
                matchedTag = null,
                storedPublishedAt = "2026-08-18T00:00:00Z",
                matchedPublishedAt = null,
                codesAlreadyMatch = false,
                isUpdate = false,
                timeZone = DeviceLocalTime.utcOffsetHours(8),
            ).format()

        assertContains(report, "branch=empty_window")
        assertContains(report, "windowSource=backend_blocked")
        assertContains(report, "windowSize=0")
        assertContains(report, "storedPublishedAtLocal=2026-08-18 08:00:00")
        assertContains(report, "matchedPublishedAtLocal=null")
        assertEquals(20, report.lines().count())
    }

    @Test
    fun local_fields_keep_utc_original_and_use_device_offset() {
        val report =
            UpdateCheckReport(
                usedTimestampLogic = true,
                branch = "timestamp",
                reason = "timestamp_not_newer",
                windowSource = "github",
                windowSize = 1,
                includePreReleases = true,
                opaqueMatched = true,
                sameTag = true,
                reconcilable = false,
                installedTag = "nightly",
                matchedTag = "nightly",
                storedPublishedAt = "2026-08-21T18:49:24Z",
                matchedPublishedAt = "2026-08-21T18:49:24Z",
                codesAlreadyMatch = false,
                isUpdate = false,
                timeZone = DeviceLocalTime.utcOffsetHours(8),
            ).format()

        assertContains(report, "matchedPublishedAt=2026-08-21T18:49:24Z")
        assertContains(report, "matchedPublishedAtLocal=2026-08-22 02:49:24")
        assertContains(report, "storedPublishedAt=2026-08-21T18:49:24Z")
        assertContains(report, "storedPublishedAtLocal=2026-08-22 02:49:24")
    }
}

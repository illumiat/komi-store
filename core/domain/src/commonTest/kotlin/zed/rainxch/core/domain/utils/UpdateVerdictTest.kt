package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateVerdictTest {


    private fun decide(
        installedTag: String = "1.0.0",
        installedVersionCode: Long = 100L,
        storedLatestTag: String? = null,
        storedLatestVersionCode: Long? = null,
        storedPublishedAt: String? = null,
        wasUpdateAvailable: Boolean = false,
        skippedTag: String? = null,
        matchedTag: String = "1.1.0",
        matchedPublishedAt: String? = "2026-08-01T00:00:00Z",
        matchedIsPrerelease: Boolean = false,
    ): UpdateVerdict.Result =
        UpdateVerdict.decide(
            installed = UpdateVerdict.Installed(installedTag, installedVersionCode),
            stored =
                UpdateVerdict.Stored(
                    latestTag = storedLatestTag,
                    latestVersionCode = storedLatestVersionCode,
                    publishedAt = storedPublishedAt,
                    wasUpdateAvailable = wasUpdateAvailable,
                ),
            matched = UpdateVerdict.Matched(matchedTag, matchedPublishedAt, matchedIsPrerelease),
            skippedTag = skippedTag,
        )


    @Test
    fun semver_newer_reports_update() {
        val result = decide(installedTag = "1.0.0", matchedTag = "1.1.0")
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun semver_not_newer_stays_silent() {
        val result = decide(installedTag = "1.2.0", matchedTag = "1.1.0")
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun beta_build_bump_reports_update() {
        // legado regression: 3.26.16-beta.20 > 3.26.16-beta.19
        val result =
            decide(
                installedTag = "3.26.16-beta.19",
                matchedTag = "3.26.16-beta.20",
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }


    @Test
    fun nightly_tag_uses_timestamp_logic() {
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "nightly",
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                storedPublishedAt = null,
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun nightly_newer_published_at_reports_update() {
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "nightly",
                storedLatestTag = "nightly",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun nightly_same_timestamp_retains_update_until_installed() {
        // Scan 1 flagged the update; scan 2 with no install in between must
        // keep it surfaced rather than silently dropping it.
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "nightly",
                storedLatestTag = "nightly",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                wasUpdateAvailable = true,
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun nightly_same_timestamp_no_baseline_change_stays_silent() {
        // After install (update flag cleared, same baseline), no stale report.
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "nightly",
                storedLatestTag = "nightly",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                wasUpdateAvailable = false,
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun nightly_null_matched_timestamp_is_silent() {
        // The helper defaults matchedPublishedAt to a valid ISO string; a null
        // matched timestamp leaves no evidence, so the timestamp branch reports
        // nothing even for an opaque marker.
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "nightly",
                matchedPublishedAt = null,
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun nightly_empty_matched_timestamp_counts_as_present() {
        // An empty string is not null: with no stored baseline the first-scan rule
        // (previousLatestPublishedAt == null && matchedPublishedAt != null) fires and
        // the timestamp branch reports the update.
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "nightly",
                matchedPublishedAt = "",
                storedPublishedAt = null,
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }


    @Test
    fun installerx_unparseable_hash_prerelease_routes_to_timestamp() {
        val result =
            decide(
                installedTag = "26.08.21fae85",
                matchedTag = "26.08.11f15e4",
                matchedPublishedAt = "2026-08-26T07:15:10Z",
                storedPublishedAt = null,
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun installerx_older_hash_still_detected_when_baseline_advances() {
        // 26.08.11f15e4 (code 1523) is NEWER than 26.08.21fae85 (code 1509)
        // despite the numeric prefix suggesting otherwise. publishedAt decides.
        val result =
            decide(
                installedTag = "26.08.21fae85",
                installedVersionCode = 1509L,
                matchedTag = "26.08.11f15e4",
                matchedPublishedAt = "2026-08-26T07:15:10Z",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }


    @Test
    fun installerx_hash_tail_routes_to_timestamp_without_the_prerelease_flag() {
        // Same inputs as installerx_unparseable_hash_prerelease_routes_to_timestamp
        // with only matchedIsPrerelease flipped to false. The tag carries a commit
        // hash tail, which says its version number names a build rather than an
        // ordering — so publish time is the question, and the maintainer's filing
        // decision on GitHub does not get a vote. The hash tail sorts below the
        // installed tag as a string ("26.08.11…" < "26.08.21…"), so string order
        // would call the newer build silent; publish time is what recognises it.
        // With no stored baseline yet the first scan reports, which is why this
        // reads as an update.
        val result =
            decide(
                installedTag = "26.08.21fae85",
                matchedTag = "26.08.11f15e4",
                matchedPublishedAt = "2026-08-26T07:15:10Z",
                storedPublishedAt = null,
                matchedIsPrerelease = false,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun installerx_reused_hash_tail_without_prerelease_flag_uses_timestamp() {
        // The tag is reused (installed and matched are the same), so the
        // sameTag && !reconcilable leg of usedTimestampLogic applies even with
        // matchedIsPrerelease = false and the newer publishedAt reports the update.
        val result =
            decide(
                installedTag = "26.08.11f15e4",
                matchedTag = "26.08.11f15e4",
                matchedPublishedAt = "2026-08-26T07:15:10Z",
                storedPublishedAt = null,
                matchedIsPrerelease = false,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun installerx_matching_codes_and_tag_make_the_rewrite_gate_open() {
        // A live stored code: with storedLatestVersionCode non-null and storedLatestTag
        // equal to the matched tag, codesAlreadyMatch is judged on real values instead
        // of short-circuiting on a null snapshot (the 1509L dead-input shape).
        //
        // The update flag follows the same route as any other tag tracked by publish
        // time: the matched release was published after the stored baseline, so the
        // tag names a newer build than the record had seen and the update is reported.
        // Codes matching does not argue against that — the code is the build's
        // ordering, publish time is what distinguishes one build of a tag from the
        // next, and only the latter can see a rebuild.
        val result =
            decide(
                installedTag = "26.08.21fae85",
                installedVersionCode = 100L,
                storedLatestTag = "26.08.11f15e4",
                storedLatestVersionCode = 100L,
                matchedTag = "26.08.11f15e4",
                matchedPublishedAt = "2026-08-26T07:15:10Z",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = false,
            )
        assertTrue(result.codesAlreadyMatch)
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun codes_match_short_circuits_to_silent() {
        val result =
            decide(
                installedTag = "1.0.0",
                installedVersionCode = 100L,
                storedLatestTag = "1.0.0",
                storedLatestVersionCode = 100L,
                matchedTag = "1.0.0",
            )
        assertFalse(result.isUpdateAvailable)
        assertTrue(result.codesAlreadyMatch)
    }


    @Test
    fun skipped_nightly_is_offered_again_once_the_tag_is_rebuilt() {
        // Skipping a rolling tag means skipping the build it names, not the tag.
        // Once CI republishes `nightly` with a newer publishedAt the declined
        // build is gone, so the skip has done its job and the new build is
        // announced like any other.
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "nightly",
                skippedTag = "nightly",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.skipBecameStale)
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun skipped_nightly_stays_skipped_while_the_build_is_unchanged() {
        // The same build seen again is not a new one — the skip still holds.
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "nightly",
                skippedTag = "nightly",
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertFalse(result.skipBecameStale)
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun skipped_nightly_survives_a_missing_timestamp_baseline() {
        // Nothing to compare against means no evidence of a rebuild. Dropping
        // the skip here would clear it in the check that just recorded it.
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "nightly",
                skippedTag = "nightly",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                storedPublishedAt = null,
                matchedIsPrerelease = true,
            )
        assertFalse(result.skipBecameStale)
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun skipped_plain_tag_outlives_a_republished_timestamp() {
        // A plain tag names one build, so a moved publishedAt is not evidence of
        // a new one. Only a strictly newer tag releases this skip.
        val result =
            decide(
                installedTag = "1.0.0",
                matchedTag = "1.1.0",
                skippedTag = "1.1.0",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                storedPublishedAt = "2026-08-01T00:00:00Z",
            )
        assertFalse(result.skipBecameStale)
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun skipped_tag_is_silent() {
        val result = decide(skippedTag = "1.1.0", matchedTag = "1.1.0")
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun skipped_tag_becomes_stale_when_newer_release_appears() {
        val result = decide(skippedTag = "1.1.0", matchedTag = "1.2.0")
        assertTrue(result.skipBecameStale)
        assertTrue(result.isUpdateAvailable)
    }


    @Test
    fun stable_vs_nightly_is_irreconcilable_and_silent() {
        // installed is a nightly tag; matched release is a stable semver tag
        // and NOT flagged as prerelease → must NOT nag (no stable→nightly
        // fallback, and no bogus numeric comparison either).
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "2.0.0",
                matchedIsPrerelease = false,
            )
        assertFalse(result.isUpdateAvailable)
    }


    @Test
    fun codes_already_match_requires_positive_codes() {
        // Both >0L guards in the codesAlreadyMatch conjunction: a zero installed code
        // trips the first guard, a zero stored code trips the second.
        val zeroInstalled =
            decide(
                installedTag = "1.0.0",
                installedVersionCode = 0L,
                storedLatestTag = "1.0.0",
                storedLatestVersionCode = 0L,
                matchedTag = "1.0.0",
            )
        assertFalse(zeroInstalled.codesAlreadyMatch)

        val zeroStored =
            decide(
                installedTag = "1.0.0",
                installedVersionCode = 100L,
                storedLatestTag = "1.0.0",
                storedLatestVersionCode = 0L,
                matchedTag = "1.0.0",
            )
        assertFalse(zeroStored.codesAlreadyMatch)
    }


    @Test
    fun rewrite_gate_rejects_when_codes_or_stored_tag_differ() {
        val matched = decide(installedTag = "1.0.0", installedVersionCode = 100L)
        assertFalse(matched.codesAlreadyMatch)

        // Equal codes are not enough on their own: the stored tag must also name the
        // same build as the matched release, or isExactSameVersion rejects the gate.
        val tagMismatch =
            decide(
                installedTag = "1.0.0",
                installedVersionCode = 100L,
                storedLatestTag = "1.0.1",
                storedLatestVersionCode = 100L,
                matchedTag = "1.0.0",
            )
        assertFalse(tagMismatch.codesAlreadyMatch)
    }


    @Test
    fun skipped_nightly_same_instant_in_offset_form_is_not_a_rebuild() {
        // "17:27:32+02:00" and "15:27:32Z" are the same instant. Under string
        // order the offset form sorts later and would wrongly release the skip.
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "nightly",
                skippedTag = "nightly",
                matchedPublishedAt = "2026-09-17T17:27:32+02:00",
                storedPublishedAt = "2026-09-17T15:27:32Z",
                matchedIsPrerelease = true,
            )
        assertFalse(result.skipBecameStale)
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun skipped_nightly_genuinely_later_offset_timestamp_releases_the_skip() {
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "nightly",
                skippedTag = "nightly",
                matchedPublishedAt = "2026-09-17T18:27:32+02:00",
                storedPublishedAt = "2026-09-17T15:27:32Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.skipBecameStale)
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun skipped_nightly_is_released_even_when_the_stored_code_already_matches_installed() {
        // The lock this route exists to break: confirmInstall reconciled the stored code to
        // the installed build and the reused tag name survived, so for the next nightly
        // `installed.code == stored.code == matched tag` still holds. Reading that as "the
        // package already is this build" used to hold the skip for good; the newer
        // publishedAt is what proves it is a different build.
        val result =
            decide(
                installedTag = "nightly",
                installedVersionCode = 500L,
                storedLatestTag = "nightly",
                storedLatestVersionCode = 500L,
                storedPublishedAt = "2026-08-01T00:00:00Z",
                skippedTag = "nightly",
                matchedTag = "nightly",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.codesAlreadyMatch)
        assertTrue(result.skipBecameStale)
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun skipped_nightly_stays_skipped_when_matching_codes_but_the_publish_time_did_not_move() {
        // The other half of the boundary: matching codes are not themselves a reason to
        // release the skip. With the publish time unchanged this is the same build the
        // user declined, so the skip holds.
        val result =
            decide(
                installedTag = "nightly",
                installedVersionCode = 500L,
                storedLatestTag = "nightly",
                storedLatestVersionCode = 500L,
                storedPublishedAt = "2026-08-01T00:00:00Z",
                skippedTag = "nightly",
                matchedTag = "nightly",
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.codesAlreadyMatch)
        assertFalse(result.skipBecameStale)
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun adopt_gate_opens_on_the_irreconcilable_self_healing_route() {
        // The row the strict gate locks up: the installed tag is a version name
        // ("26.09.01") the matched opaque tag cannot be, and codesAlreadyMatch is shut
        // because a drifting tag cleared the stored code. The irreconcilable route keeps the
        // rewrite alive; the old codesAlreadyMatch-only gate left it a dead path and the
        // stale tag in place for good.
        val codesAlreadyMatch = false
        assertFalse(codesAlreadyMatch)

        assertTrue(
            UpdateVerdict.shouldAdoptMatchedTag(
                codesAlreadyMatch = codesAlreadyMatch,
                installedTag = "26.09.01",
                matchedTag = "nightly",
            ),
        )
    }

    @Test
    fun adopt_gate_keeps_its_other_bounds() {
        // Nothing to rewrite when the tag already names the matched release.
        assertFalse(UpdateVerdict.shouldAdoptMatchedTag(true, "2.0.0", "2.0.0"))
        // Reconcilable pair with the codes not matching: neither route is open.
        assertFalse(UpdateVerdict.shouldAdoptMatchedTag(false, "1.0.0", "2.0.0"))
        // The strict route still opens it on its own.
        assertTrue(UpdateVerdict.shouldAdoptMatchedTag(true, "1.0.0", "2.0.0"))
    }

    @Test
    fun snapshot_baseline_survives_when_only_the_installed_side_is_timestamp_tracked() {
        // The pair UpdateVerdict routes to its timestamp branch through the !reconcilable
        // leg: installed "1.0.0-abc1234" (a commit-hash build) vs the matched
        // "1.1.0-beta.2". Neither tag is timestamp-tracked on its own, so the old
        // isTimestampTrackedTag(storedLatestTag) test cleared the baseline and the next
        // check re-announced the same unchanged release from a null baseline.
        assertFalse(VersionMath.isTimestampTrackedTag("1.1.0-beta.2"))
        assertFalse(VersionMath.isTimestampTrackedTag("1.0.0-abc1234"))

        assertTrue(
            VersionMath.shouldRetainSnapshotBaseline(
                installedTag = "1.0.0-abc1234",
                storedLatestTag = "1.1.0-beta.2",
            ),
        )
    }

    @Test
    fun snapshot_baseline_is_cleared_for_a_plain_comparable_pair() {
        // The self-heal direction is preserved: two ordinary semver tags carry no publish
        // time to lose, so the baseline is dropped and the next check recomputes.
        assertFalse(
            VersionMath.shouldRetainSnapshotBaseline(
                installedTag = "1.0.0",
                storedLatestTag = "1.1.0",
            ),
        )
    }
}

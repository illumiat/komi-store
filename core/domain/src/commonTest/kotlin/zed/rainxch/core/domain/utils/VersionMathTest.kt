package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionMathTest {

    @Test
    fun normalize_preserves_opaque_marker_tags() {
        assertEquals("nightly-a1b2c3d", VersionMath.normalizeVersion("nightly-a1b2c3d"))
        assertEquals("canary-deadbeef", VersionMath.normalizeVersion("canary-deadbeef"))
        assertEquals("nightly-abc123", VersionMath.normalizeVersion("vnightly-abc123"))
        assertEquals("nightly", VersionMath.normalizeVersion("nightly"))
        assertEquals("beta-x7z92", VersionMath.normalizeVersion("beta-x7z92"))
        assertEquals("rolling-abc123", VersionMath.normalizeVersion("rolling-abc123"))
        assertEquals("rolling", VersionMath.normalizeVersion("rolling"))
    }

    @Test
    fun normalize_extracts_digits_from_calver_nightly() {
        assertEquals("20260731", VersionMath.normalizeVersion("nightly-20260731"))
        assertEquals("20260801", VersionMath.normalizeVersion("nightly-20260801"))
        assertEquals("20260801", VersionMath.normalizeVersion("rolling-20260801"))
    }

    @Test
    fun normalize_semver_unaffected() {
        assertEquals("1.2.3", VersionMath.normalizeVersion("1.2.3"))
        assertEquals("1.2.3-beta", VersionMath.normalizeVersion("v1.2.3-beta"))
        assertEquals("2.0.9.1", VersionMath.normalizeVersion("2.0.9.1"))
    }

    @Test
    fun unparseable_hash_prereleases_are_not_reconcilable() {
        assertFalse(
            VersionMath.versionsReconcilable(
                "26.08.21fae85",
                "26.08.11f15e4",
            ),
        )
        assertFalse(
            VersionMath.versionsReconcilable(
                "26.08.ac0a687",
                "26.08.11f15e4",
            ),
        )
        // short hash tails (4-5 hex chars) must also opt out of numeric compare
        assertFalse(
            VersionMath.versionsReconcilable(
                "1.2.3fabc",
                "1.2.4",
            ),
        )
    }

    @Test
    fun opaque_marker_detects_release_tag_alone() {
        assertTrue(VersionMath.isOpaqueMarker("nightly"))
        assertTrue(VersionMath.isOpaqueMarker("nightly-abc"))
        assertTrue(VersionMath.isOpaqueMarker("rolling"))
        assertFalse(VersionMath.isOpaqueMarker("1.0.0"))
        assertFalse(VersionMath.isOpaqueMarker("nightly-20260731"))
    }

    @Test
    fun marker_with_a_dotted_digit_suffix_stays_numerically_comparable() {
        // A known pre-release marker followed by a hyphen and *dotted digits*
        // ("beta-1.2.3", "rc-1.0.10", "nightly-2026.08.01") is an ordinary version
        // carrying a pre-release prefix, not an opaque marker: the number after the
        // prefix is what orders it, and dropping it made two builds incomparable.
        // Reading the tag as opaque returned it verbatim, which pushed the pair into
        // a string compare where "beta-1.10.0" sorted *below* "beta-1.9.0".
        //
        // Classification is not cosmetic here — it decides which comparison runs, so
        // this test pins both halves: the classification itself, and the numeric
        // ordering that depends on it.
        assertFalse(VersionMath.isOpaqueMarker("beta-1.2.3"))
        assertFalse(VersionMath.isTimestampTrackedTag("beta-1.2.3"))
        assertFalse(VersionMath.isOpaqueMarker("rc-1.0.10"))
        assertFalse(VersionMath.isTimestampTrackedTag("rc-1.0.10"))
        assertFalse(VersionMath.isOpaqueMarker("nightly-2026.08.01"))
        assertFalse(VersionMath.isTimestampTrackedTag("nightly-2026.08.01"))
        // The payoff: the dotted digit suffix falls through to DOTTED_DIGIT_PATTERN,
        // so these compare as numbers rather than as strings.
        assertTrue(VersionMath.isVersionNewer("beta-1.10.0", "beta-1.9.0"))
        assertTrue(VersionMath.isVersionNewer("rc-1.0.10", "rc-1.0.9"))
        assertFalse(VersionMath.isVersionNewer("beta-1.9.0", "beta-1.10.0"))
    }

    @Test
    fun timestamp_tracked_covers_opaque_and_hash_tails() {
        assertTrue(VersionMath.isTimestampTrackedTag("nightly"))
        assertTrue(VersionMath.isTimestampTrackedTag("rolling"))
        // InstallerX-style hash tail is not an opaque marker but is timestamp-tracked
        assertFalse(VersionMath.isOpaqueMarker("26.08.11f15e4"))
        assertTrue(VersionMath.isTimestampTrackedTag("26.08.11f15e4"))
        assertTrue(VersionMath.isTimestampTrackedTag("1.2.3fabc"))
        assertFalse(VersionMath.isTimestampTrackedTag("1.2.3"))
        assertFalse(VersionMath.isTimestampTrackedTag(null))
    }

    @Test
    fun versions_reconcilable_semver() {
        assertTrue(VersionMath.versionsReconcilable("1.2.3", "1.2.4"))
        assertTrue(VersionMath.versionsReconcilable("v1.2.3", "1.2.3"))
    }

    @Test
    fun versions_reconcilable_rejects_hash_mismatch() {
        assertFalse(VersionMath.versionsReconcilable("2.0.9.1", "2.0.9-1c19925b5"))
        assertFalse(VersionMath.versionsReconcilable("nightly-abc", "1.2.3"))
    }

    @Test
    fun calver_nightly_compares_numerically() {
        assertTrue(VersionMath.isVersionNewer("nightly-20260801", "nightly-20260731"))
        assertFalse(VersionMath.isVersionNewer("nightly-20260731", "nightly-20260801"))
    }

    @Test
    fun semver_comparison_regression() {
        assertTrue(VersionMath.isVersionNewer("1.2.4", "1.2.3"))
        assertFalse(VersionMath.isVersionNewer("1.2.3", "1.2.4"))
        assertTrue(VersionMath.isVersionNewer("2.0.0", "1.9.9"))
        assertFalse(VersionMath.isVersionNewer("1.0.0-alpha", "1.0.0"))
    }

    @Test
    fun beta_release_comparison_detects_newer_build() {
        assertTrue(VersionMath.isVersionNewer("3.26.16-beta.20", "3.26.16-beta.19"))
        assertFalse(VersionMath.isVersionNewer("3.26.16-beta.19", "3.26.16-beta.20"))
    }

    @Test
    fun nightly_is_prerelease_tag() {
        assertTrue(VersionMath.isPreReleaseTag("nightly"))
        assertTrue(VersionMath.isPreReleaseTag("nightly-abc"))
        assertTrue(VersionMath.isPreReleaseTag("nightly-20260731"))
        assertTrue(VersionMath.isPreReleaseTag("rolling"))
        assertTrue(VersionMath.isPreReleaseTag("rolling-abc"))
        assertFalse(VersionMath.isPreReleaseTag("v1.2.3"))
        assertFalse(VersionMath.isPreReleaseTag("1.2.3"))
    }

    @Test
    fun nightly_marker_label() {
        assertEquals("Nightly", VersionMath.preReleaseMarkerLabel("nightly"))
        assertEquals("Nightly", VersionMath.preReleaseMarkerLabel("nightly-abc"))
        assertEquals("Nightly", VersionMath.preReleaseMarkerLabel("v1.2.3-nightly"))
        assertEquals("Rolling", VersionMath.preReleaseMarkerLabel("rolling"))
        assertEquals("Rolling", VersionMath.preReleaseMarkerLabel("rolling-abc"))
    }

    @Test
    fun detect_scheme_for_nightly() {
        assertEquals(VersionMath.Scheme.Unknown, VersionMath.detectScheme("nightly"))
        assertEquals(VersionMath.Scheme.Unknown, VersionMath.detectScheme("nightly-abc"))
        assertEquals(VersionMath.Scheme.SemVer, VersionMath.detectScheme("v1.2.3-nightly"))
        assertEquals(VersionMath.Scheme.CalVer, VersionMath.detectScheme("2026-07-31"))
    }

    @Test
    fun timestamp_update_retained_across_scans_without_install() {
        val stillAvailable =
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly-def",
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                previousLatestPublishedAt = "2026-08-01T00:00:00Z",
                previousWasUpdateAvailable = true,
                previousLatestTag = "nightly-def",
            )
        assertTrue(stillAvailable)
    }

    @Test
    fun timestamp_update_reports_newer_release() {
        assertTrue(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly-def",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                previousLatestPublishedAt = "2026-08-01T00:00:00Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly-abc",
            ),
        )
    }

    @Test
    fun timestamp_update_not_reported_after_install() {
        assertFalse(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly-def",
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                previousLatestPublishedAt = "2026-08-01T00:00:00Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly-def",
            ),
        )
    }

    @Test
    fun timestamp_update_first_scan_with_no_baseline() {
        assertTrue(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                previousLatestPublishedAt = null,
                previousWasUpdateAvailable = false,
                previousLatestTag = null,
            ),
        )
    }

    // --- absolute-time parsing (B-7). GitHub sends "…Z", Forgejo/Codeberg send
    // "…+HH:MM"; the three spellings below are the same instant but would order
    // differently under string comparison.

    @Test
    fun published_at_parses_z_and_numeric_offsets_to_the_same_instant() {
        val utc = VersionMath.parsePublishedAtToInstant("2026-09-17T15:27:32Z")
        val plusTwo = VersionMath.parsePublishedAtToInstant("2026-09-17T17:27:32+02:00")
        val minusSeven = VersionMath.parsePublishedAtToInstant("2026-09-17T08:27:32-07:00")

        assertEquals(1789658852000L, utc?.toEpochMilliseconds())
        assertEquals(utc, plusTwo)
        assertEquals(utc, minusSeven)
    }

    @Test
    fun published_at_parse_failure_degrades_to_null() {
        assertEquals(null, VersionMath.parsePublishedAtToInstant(null))
        assertEquals(null, VersionMath.parsePublishedAtToInstant(""))
        assertEquals(null, VersionMath.parsePublishedAtToInstant("   "))
        // local time without an offset is not a valid Instant per RFC 3339 here
        assertEquals(null, VersionMath.parsePublishedAtToInstant("2026-09-17T15:27:32"))
        assertEquals(null, VersionMath.parsePublishedAtToInstant("refs/tags/nightly"))
        assertEquals(null, VersionMath.parsePublishedAtToInstant("not-a-time"))
    }

    @Test
    fun is_published_at_after_orders_absolute_time_not_lexicographic() {
        // 17:27:32+02:00 == 15:27:32Z: equal instants are not "after".
        assertFalse(
            VersionMath.isPublishedAtAfter(
                "2026-09-17T17:27:32+02:00",
                "2026-09-17T15:27:32Z",
            ),
        )
        // "+02:00" sorts after "16:00:00Z" as a string, but 17:27+02:00 (15:27Z)
        // is EARLIER than 16:00Z — string order would have claimed newer.
        assertFalse(
            VersionMath.isPublishedAtAfter(
                "2026-09-17T17:27:32+02:00",
                "2026-09-17T16:00:00Z",
            ),
        )
        // A genuinely later offset form still reports newer.
        assertTrue(
            VersionMath.isPublishedAtAfter(
                "2026-09-17T18:27:32+02:00",
                "2026-09-17T15:27:32Z",
            ),
        )
        // Unparseable side is never "after".
        assertFalse(VersionMath.isPublishedAtAfter("not-a-time", "2026-09-17T15:27:32Z"))
        assertFalse(VersionMath.isPublishedAtAfter("2026-09-17T15:27:32Z", "not-a-time"))
    }

    @Test
    fun timestamp_update_treats_equal_instants_across_offset_forms_as_not_newer() {
        assertFalse(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-17T17:27:32+02:00",
                previousLatestPublishedAt = "2026-09-17T15:27:32Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly",
            ),
        )
    }

    @Test
    fun timestamp_update_reports_offset_form_that_is_truly_newer() {
        assertTrue(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-17T18:27:32+02:00",
                previousLatestPublishedAt = "2026-09-17T15:27:32Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly",
            ),
        )
    }

    @Test
    fun timestamp_update_degrades_to_silent_when_either_side_is_unparseable() {
        assertFalse(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "not-a-time",
                previousLatestPublishedAt = "2026-08-01T00:00:00Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly",
            ),
        )
        assertFalse(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                previousLatestPublishedAt = "not-a-time",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly",
            ),
        )
    }

    @Test
    fun asset_identity_reports_a_swapped_asset_at_the_same_publish_time() {
        // The case `published_at` cannot see: the Release is not re-created, only its
        // asset is replaced — what `gh release upload --clobber` does, and what most
        // CI asset-upload steps do. Measured on a release of this project's own fork:
        // published_at stayed put while the asset changed.
        assertTrue(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-24T11:46:11Z",
                previousLatestPublishedAt = "2026-09-24T11:46:11Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly",
                matchedAssetDigest = "sha256:bbbb",
                matchedAssetSize = 70_543_755L,
                previousAssetDigest = "sha256:aaaa",
                previousAssetSize = 70_543_755L,
            ),
        )
    }

    @Test
    fun asset_identity_stays_quiet_when_the_asset_is_the_same_one() {
        // The guard against the false-positive class this project already had to fix:
        // same release, same bytes, nothing to report — not even a note edit would
        // show here, which is why the digest is read instead of the release's
        // `updated_at`.
        assertFalse(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-24T11:46:11Z",
                previousLatestPublishedAt = "2026-09-24T11:46:11Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly",
                matchedAssetDigest = "sha256:aaaa",
                matchedAssetSize = 70_543_755L,
                previousAssetDigest = "sha256:aaaa",
                previousAssetSize = 70_543_755L,
            ),
        )
    }

    @Test
    fun asset_identity_falls_back_to_size_and_says_nothing_without_evidence() {
        assertTrue(VersionMath.assetIdentityChanged(
            matchedDigest = null,
            matchedSize = 2_000L,
            storedDigest = null,
            storedSize = 1_000L,
        ))
        // Digest present on both sides wins over a size that happens to match.
        assertTrue(VersionMath.assetIdentityChanged(
            matchedDigest = "sha256:bbbb",
            matchedSize = 1_000L,
            storedDigest = "sha256:aaaa",
            storedSize = 1_000L,
        ))
        // No digest on either side and no size on either side is not evidence.
        assertFalse(VersionMath.assetIdentityChanged(
            matchedDigest = null,
            matchedSize = null,
            storedDigest = null,
            storedSize = null,
        ))
        // One side missing is deliberately not folded into the other signal: a host
        // that started (or stopped) supplying digests must not read as a rebuild.
        assertFalse(VersionMath.assetIdentityChanged(
            matchedDigest = "sha256:aaaa",
            matchedSize = null,
            storedDigest = null,
            storedSize = null,
        ))
    }
}

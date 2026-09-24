package zed.rainxch.core.domain.model.installation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import zed.rainxch.core.domain.utils.UpdateVerdict

class InstalledAppUpdatesTest {

    private fun app(
        installedVersion: String = "1.0.0",
        latestVersion: String? = "2.0.0",
        latestVersionCode: Long? = 200L,
        latestVersionName: String? = "2.0.0",
        isUpdateAvailable: Boolean = true,
        isPendingInstall: Boolean = false,
        pendingFilePath: String? = "/data/parked.apk",
    ): InstalledApp = InstalledApp(
        packageName = "com.example.app",
        repoId = 1L,
        repoName = "app",
        repoOwner = "owner",
        repoOwnerAvatarUrl = "https://avatar",
        repoDescription = null,
        primaryLanguage = "Kotlin",
        repoUrl = "https://github.com/owner/app",
        installedVersion = installedVersion,
        installedAssetName = "app-1.0.0.apk",
        installedAssetUrl = "https://dl/app-1.0.0.apk",
        latestVersion = latestVersion,
        latestAssetName = "app-2.0.0.apk",
        latestAssetUrl = "https://dl/app-2.0.0.apk",
        latestAssetSize = 1024L,
        appName = "App",
        installSource = InstallSource.THIS_APP,
        installedAt = 1000L,
        lastCheckedAt = 2000L,
        lastUpdatedAt = 1500L,
        isUpdateAvailable = isUpdateAvailable,
        signingFingerprint = "SHA",
        systemArchitecture = "arm64-v8a",
        fileExtension = "apk",
        isPendingInstall = isPendingInstall,
        installedVersionName = "1.0.0",
        installedVersionCode = 100L,
        latestVersionName = latestVersionName,
        latestVersionCode = latestVersionCode,
        latestReleasePublishedAt = "2026-08-01T00:00:00Z",
        pendingInstallFilePath = pendingFilePath,
        pendingInstallVersion = if (pendingFilePath != null) "2.0.0" else null,
        pendingInstallAssetName = if (pendingFilePath != null) "app-2.0.0.apk" else null,
    )

    @Test
    fun confirmInstallWritesInstallZone() {
        val result = app().confirmInstall(
            tag = "2.0.0",
            assetName = "app-2.0.0.apk",
            assetUrl = "https://dl/app-2.0.0.apk",
            versionName = "2.0.0",
            versionCode = 200L,
            signingFingerprint = "SHA-NEW",
            at = 9999L,
        )

        assertEquals("2.0.0", result.installedVersion)
        assertEquals("app-2.0.0.apk", result.installedAssetName)
        assertEquals("https://dl/app-2.0.0.apk", result.installedAssetUrl)
        assertEquals("2.0.0", result.installedVersionName)
        assertEquals(200L, result.installedVersionCode)
        assertEquals("SHA-NEW", result.signingFingerprint)
        assertEquals(9999L, result.lastUpdatedAt)
        assertEquals(9999L, result.lastCheckedAt)
        assertFalse(result.isPendingInstall)
    }

    @Test
    fun confirmInstallClearsUpdateFlagWhenLatestMatchesInstalled() {
        val result = app().confirmInstall(
            tag = "2.0.0",
            assetName = "a",
            assetUrl = "u",
            versionName = "2.0.0",
            versionCode = 200L,
            signingFingerprint = null,
            at = 1L,
        )
        assertFalse(result.isUpdateAvailable)
        // snapshot reconciled to the installed code
        assertEquals(200L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallKeepsUpdateFlagWhenSnapshotStillNewer() {
        val result = app(latestVersion = "3.0.0", latestVersionCode = 300L).confirmInstall(
            tag = "2.0.0",
            assetName = "a",
            assetUrl = "u",
            versionName = "2.0.0",
            versionCode = 200L,
            signingFingerprint = null,
            at = 1L,
        )
        assertTrue(result.isUpdateAvailable)
        assertEquals(300L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallKeepsFlagWhenLandedBuildIsOlderThanTarget() {
        // B-1: the system installed an older build than the tracked target. The red
        // dot must survive and latestVersionCode must NOT be reconciled downward,
        // otherwise the update could never be offered again.
        val result =
            app(latestVersion = "2.0.0", latestVersionCode = 200L).confirmInstall(
                tag = "2.0.0",
                assetName = "a",
                assetUrl = "u",
                versionName = "1.5.0",
                versionCode = 150L,
                signingFingerprint = null,
                at = 1L,
            )
        assertTrue(result.isUpdateAvailable)
        assertEquals(200L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallKeepsFlagWhenLandedCodeIsBelowTargetCode() {
        // Same version string but the landed versionCode is below the target's: the
        // requested build was not actually installed, so the flag stays and the
        // snapshot is left at the target code.
        val result =
            app(latestVersion = "2.0.0", latestVersionCode = 200L).confirmInstall(
                tag = "2.0.0",
                assetName = "a",
                assetUrl = "u",
                versionName = "2.0.0",
                versionCode = 199L,
                signingFingerprint = null,
                at = 1L,
            )
        assertTrue(result.isUpdateAvailable)
        assertEquals(200L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallKeepsFlagForTimestampTrackedTarget() {
        // A timestamp-tracked target (an opaque marker such as `nightly`) names many
        // builds, so confirming one of them proves nothing: isVersionNewer(tag, tag) is
        // false and the landed code equals the target code, so the code test is silent
        // too. Flag and snapshot code are left as they were — clearing them would wipe the
        // evidence the timestamp branch compares publish times against, and the update
        // would never be offered again. (This replaces the former claim that an opaque
        // target clears the flag once the code reaches it, which the fix narrowed.)
        val result =
            app(
                latestVersion = "nightly",
                latestVersionCode = 500L,
                isUpdateAvailable = true,
            ).confirmInstall(
                tag = "nightly",
                assetName = "a",
                assetUrl = "u",
                versionName = "26.09.01",
                versionCode = 500L,
                signingFingerprint = null,
                at = 1L,
            )
        assertTrue(result.isUpdateAvailable)
        assertEquals(500L, result.latestVersionCode)
        assertEquals("nightly", result.installedVersion)
    }

    @Test
    fun confirmInstallClearsUpdateFlagWhenSnapshotMissing() {
        val result = app(latestVersion = null, latestVersionCode = null).confirmInstall(
            tag = "1.0.0",
            assetName = "a",
            assetUrl = "u",
            versionName = "1.0.0",
            versionCode = 100L,
            signingFingerprint = null,
            at = 1L,
        )
        assertFalse(result.isUpdateAvailable)
        assertEquals(100L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallClearsFlagWhenSameTagCarriesNewerCode() {
        // A rebuild under the same tag: the version string cannot show it, but the
        // confirmation is for the current build, so the flag clears and the snapshot
        // reconciles to the just-installed code.
        val result =
            app(latestVersion = "2.0.0", latestVersionCode = 200L).confirmInstall(
                tag = "2.0.0",
                assetName = "a",
                assetUrl = "u",
                versionName = "2.0.0",
                versionCode = 201L,
                signingFingerprint = null,
                at = 1L,
            )
        assertFalse(result.isUpdateAvailable)
        assertEquals(201L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallPendingHandoffKeepsParkedMetadata() {
        val result = app().confirmInstall(
            tag = "2.0.0",
            assetName = "a",
            assetUrl = "u",
            versionName = "2.0.0",
            versionCode = 200L,
            signingFingerprint = null,
            isPending = true,
            at = 1L,
        )
        assertTrue(result.isPendingInstall)
        assertEquals("/data/parked.apk", result.pendingInstallFilePath)
        assertEquals("2.0.0", result.pendingInstallVersion)
    }

    @Test
    fun confirmInstallDoesNotTouchCheckZoneSnapshot() {
        val result = app().confirmInstall(
            tag = "2.0.0",
            assetName = "a",
            assetUrl = "u",
            versionName = "2.0.0",
            versionCode = 200L,
            signingFingerprint = null,
            at = 1L,
        )
        // check zone fields other than the code reconciliation stay untouched
        assertEquals("2.0.0", result.latestVersion)
        assertEquals("app-2.0.0.apk", result.latestAssetName)
        assertEquals("2026-08-01T00:00:00Z", result.latestReleasePublishedAt)
        assertEquals("2.0.0", result.latestVersionName)
    }

    @Test
    fun resolvePendingFromSystemAdoptsTagAndClearsPending() {
        val result = app(isPendingInstall = true).resolvePendingFromSystem(
            resolvedTag = "2.0.0",
            versionName = "2.0.0",
            versionCode = 200L,
        )
        assertFalse(result.isPendingInstall)
        assertEquals("2.0.0", result.installedVersion)
        assertEquals("2.0.0", result.installedVersionName)
        assertEquals(200L, result.installedVersionCode)
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun resolvePendingFromSystemKeepsUpdateFlagWhenSnapshotNewer() {
        val result = app(latestVersionCode = 300L).resolvePendingFromSystem(
            resolvedTag = "2.0.0",
            versionName = "2.0.0",
            versionCode = 200L,
        )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun updateFlagIsFalseWhenSnapshotCodeIsNull() {
        val result =
            app(latestVersionCode = null).resolvePendingFromSystem(
                resolvedTag = "2.0.0",
                versionName = "2.0.0",
                versionCode = 100L,
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun updateFlagIsFalseWhenSnapshotCodeIsZero() {
        val result =
            app(latestVersionCode = 0L).observeExternalInstall(
                versionName = "2.0.0",
                versionCode = 100L,
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun observeExternalInstallKeepsInstalledTagBelowTheSnapshot() {
        // Observe may adopt the installed tag only on evidence that the package is the
        // snapshot build. Code 120 does not equal the snapshot's 200, so there is no
        // such evidence and the tag is left alone. (This replaces the former blanket
        // claim that observe never touches the installed tag, which the verdict narrowed.)
        val result = app().observeExternalInstall(
            versionName = "1.2.0",
            versionCode = 120L,
        )
        assertEquals("1.0.0", result.installedVersion)
        assertEquals("1.2.0", result.installedVersionName)
        assertEquals(120L, result.installedVersionCode)
        // 200 > 120 → still an update available
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun observeExternalInstallAdoptsSnapshotTagOnceObservationReachesIt() {
        // The observation proves the package is the snapshot build (system code equals the
        // snapshot's code), so the tag naming that build is the snapshot's and observe
        // adopts it. The update flag clears because nothing is left above the snapshot.
        val result = app().observeExternalInstall(
            versionName = "2.0.0",
            versionCode = 200L,
        )
        assertEquals("2.0.0", result.installedVersion)
        assertEquals("2.0.0", result.installedVersionName)
        assertEquals(200L, result.installedVersionCode)
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun observeExternalInstallDetectsDowngradeAsUpdateAvailable() {
        val result = app(latestVersionCode = 200L).observeExternalInstall(
            versionName = "0.9.0",
            versionCode = 50L,
        )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun withSkippedReleaseWritesTheSkipAndSuppressesTheFlagItNames() {
        // Two zones on purpose: this is a declared cross-side owner. A skip naming the
        // record's own snapshot has to bring the update flag down with it — confirmInstall
        // has just recomputed that flag as "up" from the same snapshot, and left standing
        // it would advertise the release the skip was written to stop offering.
        val skipped = app().withSkippedRelease("2.0.0")
        assertEquals("2.0.0", skipped.skippedReleaseTag)
        assertFalse(skipped.isUpdateAvailable)
        // everything else untouched
        assertEquals("1.0.0", skipped.installedVersion)
        assertEquals(100L, skipped.installedVersionCode)
        assertEquals("2.0.0", skipped.latestVersion)
        assertEquals(200L, skipped.latestVersionCode)
        assertFalse(skipped.isPendingInstall)

        // A skip naming a different release says nothing about this one.
        val other = app().withSkippedRelease("3.0.0")
        assertEquals("3.0.0", other.skippedReleaseTag)
        assertTrue(other.isUpdateAvailable)

        // A skip can only suppress, never raise.
        val notAvailable = app(isUpdateAvailable = false).withSkippedRelease("3.0.0")
        assertFalse(notAvailable.isUpdateAvailable)

        // null is how a skip is released; it does not raise the flag either.
        val released = app(isUpdateAvailable = false).withSkippedRelease("2.0.0").withSkippedRelease(null)
        assertNull(released.skippedReleaseTag)
        assertFalse(released.isUpdateAvailable)
    }

    @Test
    fun confirmInstallThenWithSkippedReleaseKeepsBothWrites() {
        // The repository's confirm path, in its real order: confirmInstall recomputes the
        // flag from the snapshot the record still holds, then the skip overrides it.
        val result =
            app()
                .confirmInstall(
                    tag = "1.5.0",
                    assetName = "app-1.5.0.apk",
                    assetUrl = "https://dl/app-1.5.0.apk",
                    versionName = "1.5.0",
                    versionCode = 150L,
                    signingFingerprint = "SHA",
                    at = 3000L,
                )
                .withSkippedRelease("2.0.0")

        assertEquals("1.5.0", result.installedVersion)
        assertEquals(150L, result.installedVersionCode)
        assertEquals("2.0.0", result.skippedReleaseTag)
        // the snapshot the skip names is still the one the record compares against, and its
        // code stays paired with it rather than following the build that landed
        assertEquals("2.0.0", result.latestVersion)
        assertEquals(200L, result.latestVersionCode)
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun confirmInstallAloneStillRaisesTheFlagForTheNewerSnapshot() {
        // Without a skip the confirm path reports the release it did not reach: the
        // suppression above is the skip's doing, not confirmInstall's.
        val notSkipped =
            app().confirmInstall(
                tag = "1.5.0",
                assetName = "app-1.5.0.apk",
                assetUrl = "https://dl/app-1.5.0.apk",
                versionName = "1.5.0",
                versionCode = 150L,
                signingFingerprint = "SHA",
                at = 3000L,
            )

        assertTrue(notSkipped.isUpdateAvailable)
        assertEquals(200L, notSkipped.latestVersionCode)
        assertNull(notSkipped.skippedReleaseTag)
    }

    @Test
    fun markAndClearPendingTouchOnlyPendingFlag() {
        val marked = app().markPending()
        assertTrue(marked.isPendingInstall)
        assertEquals("/data/parked.apk", marked.pendingInstallFilePath)

        val cleared = app(isPendingInstall = true).clearPending()
        assertFalse(cleared.isPendingInstall)
    }

    @Test
    fun withLatestSnapshotWritesOnlyCheckZone() {
        val result = app().withLatestSnapshot(
            version = "nightly",
            assetName = "app-nightly.apk",
            assetUrl = "https://dl/app-nightly.apk",
            versionName = "26.09.01",
            versionCode = 999L,
        )
        assertEquals("nightly", result.latestVersion)
        assertEquals("app-nightly.apk", result.latestAssetName)
        assertEquals("https://dl/app-nightly.apk", result.latestAssetUrl)
        assertEquals("26.09.01", result.latestVersionName)
        assertEquals(999L, result.latestVersionCode)
        // install zone untouched
        assertEquals("1.0.0", result.installedVersion)
        assertEquals(100L, result.installedVersionCode)
        assertFalse(result.isPendingInstall)
    }

    @Test
    fun chainedPreInstallHandoffCombinesBothZones() {
        val result = app()
            .markPending()
            .withLatestSnapshot(
                version = "3.0.0",
                assetName = "a3",
                assetUrl = "u3",
                versionName = "3.0.0",
                versionCode = 300L,
            )
        assertTrue(result.isPendingInstall)
        assertEquals("3.0.0", result.latestVersion)
        assertEquals("1.0.0", result.installedVersion)
    }

    @Test
    fun migratedVersionInfoAlignsBothSides() {
        val result = app().withMigratedVersionInfo(
            versionName = "1.5.0",
            versionCode = 150L,
        )
        assertEquals("1.5.0", result.installedVersionName)
        assertEquals(150L, result.installedVersionCode)
        assertEquals("1.5.0", result.latestVersionName)
        assertEquals(150L, result.latestVersionCode)
        assertEquals("1.0.0", result.installedVersion)
        assertEquals("2.0.0", result.latestVersion)
    }

    @Test
    fun normalizeInstalledTagAlignsTagAndForwardsTheVerdict() {
        // The verdict is forwarded, not forced to false: for a tag that names many builds
        // the same code can be a new build, so a stored true is a real verdict that the
        // old hard-coded false would have wiped (same rule as adoptMatchedTag).
        val result =
            app(installedVersion = "1.9.0", isUpdateAvailable = true).normalizeInstalledTag(
                tag = "2.0.0",
                isUpdateAvailable = true,
            )
        assertEquals("2.0.0", result.installedVersion)
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun normalizeInstalledTagKeepsAClearedVerdictCleared() {
        // The other direction: a verdict of false stays false. The function only carries
        // the caller's decision, it never invents one.
        val result =
            app(installedVersion = "1.9.0", isUpdateAvailable = false).normalizeInstalledTag(
                tag = "2.0.0",
                isUpdateAvailable = false,
            )
        assertEquals("2.0.0", result.installedVersion)
        assertFalse(result.isUpdateAvailable)
        // snapshot fields are left alone
        assertEquals(200L, result.latestVersionCode)
    }

    @Test
    fun normalizeInstalledTagLeavesPendingAndSnapshotFieldsUntouched() {
        // pendingInstallVersion ("2.0.0") differs from latestVersion ("3.0.0"); a tag
        // normalization rewrites installedVersion only and must not disturb either,
        // nor the pending flag.
        val result =
            app(latestVersion = "3.0.0", latestVersionCode = 300L, isPendingInstall = true)
                .normalizeInstalledTag(tag = "2.0.0", isUpdateAvailable = false)
        assertEquals("2.0.0", result.installedVersion)
        assertTrue(result.isPendingInstall)
        assertEquals("2.0.0", result.pendingInstallVersion)
        assertEquals("3.0.0", result.latestVersion)
        assertEquals(300L, result.latestVersionCode)
    }

    @Test
    fun resolvePendingFromSystemKeepsTheOldTagWhenTheInstallDidNotReachTarget() {
        // The parked tag names the target, but the system reports an older code — the
        // dialog was cancelled or the install failed. Stamping the target tag would leave
        // installedVersionCode on the old code while installedVersion claims the target, and
        // the next sameTag comparison would read equal and drop the pending update.
        val result =
            app(
                installedVersion = "1.0.0",
                latestVersion = "2.0.0",
                latestVersionCode = 200L,
                isPendingInstall = true,
            ).resolvePendingFromSystem(
                resolvedTag = "2.0.0",
                versionName = "1.0.0",
                versionCode = 100L,
            )
        assertEquals("1.0.0", result.installedVersion)
        assertFalse(result.isPendingInstall)
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun resolvePendingFromSystemAdoptsTheParkedTagWhenTheInstallReachedTarget() {
        // pendingInstallVersion ("2.0.0") differs from the resolvedTag ("3.0.0"): the parked
        // tag names the release actually handed to the installer and outranks the snapshot's
        // tag, which a checkForUpdates may have moved on during the install window.
        val result =
            app(
                installedVersion = "1.0.0",
                latestVersion = "3.0.0",
                latestVersionCode = 300L,
                isPendingInstall = true,
            ).resolvePendingFromSystem(
                resolvedTag = "3.0.0",
                versionName = "1.5.0",
                versionCode = 300L,
            )
        assertEquals("2.0.0", result.installedVersion)
        assertFalse(result.isPendingInstall)
    }

    @Test
    fun externalInstallFlagIsNotFooledByAStaleInstalledTag() {
        // PackageEventReceiver backstop regression: the external install landed build 2.0.0
        // (code 200) while the stored installed tag is still the old "1.0.0" and the
        // snapshot code was cleared to null by an earlier tag drift. Re-deriving the flag
        // from that stale tag is what the pre-fix checkForUpdates did — it reports an update
        // that is already installed. The replayed, package-grounded verdict stays silent.
        val afterExternalInstall =
            app(installedVersion = "1.0.0", latestVersion = "2.0.0", latestVersionCode = null)

        val preFix = UpdateVerdict.decide(
            installed = UpdateVerdict.Installed("1.0.0", 200L),
            stored = UpdateVerdict.Stored(
                latestTag = "2.0.0",
                latestVersionCode = null,
                publishedAt = "2026-08-01T00:00:00Z",
                wasUpdateAvailable = false,
            ),
            matched = UpdateVerdict.Matched("2.0.0", "2026-08-01T00:00:00Z", false),
            skippedTag = null,
        )
        assertTrue(preFix.isUpdateAvailable)

        assertFalse(
            afterExternalInstall.externalInstallUpdateFlag(
                newVersionName = "2.0.0",
                newVersionCode = 200L,
            ),
        )
    }

    @Test
    fun externalInstallFlagStillReportsANewerRelease() {
        // The replay is not a blanket "clear": when the refreshed snapshot names a build
        // above the installed one, the update is still reported.
        val refreshed =
            app(
                installedVersion = "2.0.0",
                latestVersion = "3.0.0",
                latestVersionCode = null,
                latestVersionName = "3.0.0",
            )
        assertTrue(
            refreshed.externalInstallUpdateFlag(
                newVersionName = "2.0.0",
                newVersionCode = 200L,
            ),
        )
    }

    @Test
    fun snapshotComparisonRaisesTheFlagWhenTheBaselineSurvivedButTheCodeDidNot() {
        // The replace-without-target path: latestVersionCode was nulled by a tag drift, so
        // the code test cannot see the outstanding build — but latestVersion still names one
        // above the versionName the system reports, so the flag must not be dropped. The
        // other direction stays silent when the installed build is the snapshot's.
        val drifted = app(latestVersion = "3.0.0", latestVersionCode = null)
        assertTrue(
            drifted.snapshotStillNamesNewerBuild(
                installedCode = 200L,
                installedVersion = "2.0.0",
            ),
        )
        assertFalse(
            drifted.snapshotStillNamesNewerBuild(
                installedCode = 300L,
                installedVersion = "3.0.0",
            ),
        )
    }
}

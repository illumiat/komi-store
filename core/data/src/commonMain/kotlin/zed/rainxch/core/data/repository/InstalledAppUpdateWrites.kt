package zed.rainxch.core.data.repository

import co.touchlab.kermit.Logger
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.utils.UpdateVerdict
import zed.rainxch.core.domain.utils.VersionMath

// The Details page names one release and one asset. Persisting that pair is what lets the Library
// card describe the release the page shows instead of a generic filtered pick from the window.
// The write keeps the compare-and-set on updateStateRevision: a rejected attempt re-reads the row
// and judges again, so a concurrent writer cannot be overwritten with a stale verdict.
@Suppress("ReturnCount")
internal suspend fun persistRefreshedRelease(
    dao: InstalledAppDao,
    packageName: String,
    release: GithubRelease,
    asset: GithubAsset,
): Boolean {
    val initial = dao.getAppByPackage(packageName) ?: return false
    if (!initial.updateCheckEnabled) return false
    if (writeRefreshedRelease(dao, initial, release, asset) > 0) return true

    val retryRow = dao.getAppByPackage(packageName)?.takeIf { it.updateCheckEnabled } ?: return false
    if (writeRefreshedRelease(dao, retryRow, release, asset) > 0) return true

    // Both attempts lost the race. The row that exists now is the only truth: another writer may
    // already have recorded this release.
    val stored = dao.getAppByPackage(packageName) ?: return false
    return stored.latestReleaseId == release.id && stored.latestAssetId == asset.id
}

// One compare-and-set against the revision the row holds. A rejection is logged here because only
// the caller knows whether it will re-read the row and try again.
private suspend fun writeRefreshedRelease(
    dao: InstalledAppDao,
    row: InstalledAppEntity,
    release: GithubRelease,
    asset: GithubAsset,
): Int =
    dao
        .updateVersionInfo(
            packageName = row.packageName,
            available = refreshedReleaseIsUpdate(row, release, asset),
            version = release.tagName,
            assetName = asset.name,
            assetUrl = asset.downloadUrl,
            assetSize = asset.size,
            releaseId = release.id,
            assetId = asset.id,
            assetDigest = asset.digest,
            releaseNotes = release.description ?: "",
            timestamp = System.currentTimeMillis(),
            latestVersionName = release.tagName,
            latestVersionCode = if (release.tagName == row.latestVersion) row.latestVersionCode else null,
            latestReleasePublishedAt = release.publishedAt,
            expectedRevision = row.updateStateRevision,
            nextRevision = row.updateStateRevision + 1,
        ).also { written ->
            if (written == 0) {
                Logger.w {
                    "Refreshed release for ${row.packageName} lost revision ${row.updateStateRevision}"
                }
            }
        }

// Same verdict and helpers as a full update check, minus the installed-file hashing: a refresh
// may not touch the disk, and the stored identity already says which build the device runs.
private fun refreshedReleaseIsUpdate(
    row: InstalledAppEntity,
    release: GithubRelease,
    asset: GithubAsset,
): Boolean {
    // A detail refresh can arrive after an external install cleared the binding. Without an
    // installed asset identity, the same tag cannot prove the bytes changed; reporting it would
    // create a permanent same-version update.
    if (row.installedAssetId == null &&
        row.installedAssetDigest == null &&
        VersionMath.isExactSameVersion(release.tagName, row.installedVersion)
    ) {
        return false
    }
    val bound =
        if (row.installedAssetId == null && row.installedAssetDigest == null) {
            null
        } else {
            UpdateVerdict.Bound(
                assetId = row.installedAssetId,
                assetDigest = row.installedAssetDigest,
                releasePublishedAt = row.installedReleasePublishedAt,
            )
        }
    return UpdateVerdict
        .decide(
            installed =
                UpdateVerdict.Installed(
                    tag = row.installedVersion,
                    versionCode = row.installedVersionCode,
                    versionName = row.installedVersionName,
                ),
            stored =
                UpdateVerdict.Stored(
                    latestTag = row.latestVersion,
                    latestVersionCode = row.latestVersionCode,
                    publishedAt = row.latestReleasePublishedAt,
                    wasUpdateAvailable = row.isUpdateAvailable,
                    latestReleaseId = row.latestReleaseId,
                    latestAssetId = row.latestAssetId,
                    latestAssetDigest = row.latestAssetDigest,
                    latestAssetSize = row.latestAssetSize,
                ),
            matched =
                UpdateVerdict.Matched(
                    tag = release.tagName,
                    publishedAt = release.publishedAt,
                    isPrerelease = release.isPrerelease,
                    releaseId = release.id,
                    assetId = asset.id,
                    assetDigest = asset.digest,
                    assetSize = asset.size,
                ),
            skippedTag = row.skippedReleaseTag,
            bound = bound,
        ).isUpdateAvailable
}

// A generic check can read an older cached window. The row already naming a later release means
// the fresher result must survive, so nothing from this window may land. An equal timestamp still
// writes: a build can be replaced within the precision the forge reports, and release identity is
// then the only difference.
internal fun windowWouldRegressStoredRelease(
    app: InstalledAppEntity,
    matchedRelease: GithubRelease,
): Boolean {
    val regresses =
        app.latestReleaseId != matchedRelease.id &&
            VersionMath.publishedAtAfter(app.latestReleasePublishedAt, matchedRelease.publishedAt)
    if (regresses) {
        Logger.w {
            "Keeping the stored release for ${app.packageName}: ${app.latestVersion} " +
                "(${app.latestReleasePublishedAt}) is newer than the window's " +
                "${matchedRelease.tagName} (${matchedRelease.publishedAt})"
        }
    }
    return regresses
}

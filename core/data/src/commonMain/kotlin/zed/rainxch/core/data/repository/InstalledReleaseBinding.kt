package zed.rainxch.core.data.repository

import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.dao.resolveInstalledReleasePublishedAt
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.network.DigestVerifier
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.utils.UpdateVerdict
import zed.rainxch.core.domain.utils.VersionMath

// The verdict compares the installed build with the matched release. The stored identity is
// preferred, the fetched window covers deletes/rotation, and a legacy row without a digest gets
// one local confirmation only when the timestamp moved (a suspected re-release).
@Suppress("LongParameterList")
internal suspend fun resolveInstalledBinding(
    app: InstalledAppEntity,
    releases: List<GithubRelease>,
    matchedRelease: GithubRelease,
    matchedAsset: GithubAsset,
    dao: InstalledAppDao,
    packageMonitor: PackageMonitor,
    verifier: DigestVerifier,
): UpdateVerdict.Bound? {
    if (app.installedAssetId == null && app.installedAssetDigest == null) return null
    val installedPublishedAt = dao.resolveInstalledReleasePublishedAt(app, releases)
    val confirmedDigest =
        confirmInstalledDigest(
            app = app,
            matchedTag = matchedRelease.tagName,
            matchedPublishedAt = matchedRelease.publishedAt,
            installedPublishedAt = installedPublishedAt,
            matchedDigest = matchedAsset.digest,
            installedApkPath = packageMonitor.getInstalledPackageInfo(app.packageName)?.apkPath,
            verifier = verifier,
        )
    if (confirmedDigest != null && confirmedDigest == matchedAsset.digest) {
        // The installed bytes are the release's bytes: bind them once so later checks compare
        // digests instead of hashing the APK again.
        runCatching { dao.setInstalledAssetDigest(app.packageName, confirmedDigest) }
    }
    return UpdateVerdict.Bound(
        assetId = app.installedAssetId,
        assetDigest = app.installedAssetDigest ?: confirmedDigest,
        releasePublishedAt = installedPublishedAt,
    )
}

@Suppress("LongParameterList")
private suspend fun confirmInstalledDigest(
    app: InstalledAppEntity,
    matchedTag: String,
    matchedPublishedAt: String?,
    installedPublishedAt: String?,
    matchedDigest: String?,
    installedApkPath: String?,
    verifier: DigestVerifier,
): String? {
    val legacyWithoutDigest =
        app.installedAssetDigest == null &&
            app.installedAssetId != null &&
            matchedDigest != null
    val runsTag = VersionMath.isExactSameVersion(matchedTag, app.installedVersion)
    val timestampMoved = VersionMath.publishedAtAfter(matchedPublishedAt, installedPublishedAt)
    return when {
        !legacyWithoutDigest || !runsTag || !timestampMoved -> null
        installedApkPath == null -> null
        else -> verifier.computeSha256(installedApkPath)
    }
}

package zed.rainxch.core.data.repository

import co.touchlab.kermit.Logger
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.dao.adoptMatchedTag
import zed.rainxch.core.data.local.db.dao.resolveInstalledReleasePublishedAt
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.installation.SystemPackageInfo
import zed.rainxch.core.domain.network.DigestVerifier
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.utils.UpdateVerdict
import zed.rainxch.core.domain.utils.VersionMath

internal data class ResolvedBinding(
    val bound: UpdateVerdict.Bound?,
)

// The installer attribution cannot decide whether a stored identity still describes the
// installed bytes: Shizuku/Dhizuku/third-party installer paths all install for this app but
// report another installer package. The binding is therefore never dropped here; a legacy row
// without a digest keeps the timestamp-gated confirmation.
@Suppress("LongParameterList")
internal suspend fun resolveInstalledBinding(
    app: InstalledAppEntity,
    releases: List<GithubRelease>,
    matchedRelease: GithubRelease,
    matchedAsset: GithubAsset,
    dao: InstalledAppDao,
    packageMonitor: PackageMonitor,
    verifier: DigestVerifier,
): ResolvedBinding {
    val info = packageMonitor.getInstalledPackageInfo(app.packageName)
    val hasBinding = app.installedAssetId != null || app.installedAssetDigest != null
    if (!hasBinding) return ResolvedBinding(bound = null)
    return resolveBound(app, releases, matchedRelease, matchedAsset, info, dao, verifier)
}

@Suppress("LongParameterList")
private suspend fun resolveBound(
    app: InstalledAppEntity,
    releases: List<GithubRelease>,
    matchedRelease: GithubRelease,
    matchedAsset: GithubAsset,
    info: SystemPackageInfo?,
    dao: InstalledAppDao,
    verifier: DigestVerifier,
): ResolvedBinding {
    val installedPublishedAt = dao.resolveInstalledReleasePublishedAt(app, releases)
    val storedDigest = app.installedAssetDigest
    val matchedDigest = matchedAsset.digest
    val proven = storedDigest != null && matchedDigest != null && storedDigest == matchedDigest
    val legacyProven =
        if (!proven && matchedDigest != null) {
            confirmLegacyBinding(
                app = app,
                matchedRelease = matchedRelease,
                matchedDigest = matchedDigest,
                installedPublishedAt = installedPublishedAt,
                info = info,
                verifier = verifier,
            )
        } else {
            null
        }
    val bytesProven = proven || (legacyProven != null && legacyProven == matchedDigest)
    if (legacyProven != null && legacyProven == matchedDigest) {
        rebindProvenIdentity(app, matchedRelease, matchedAsset, matchedDigest, dao)
    }
    val provenDigest = matchedDigest.takeIf { bytesProven }
    val bound =
        if (provenDigest != null) {
            UpdateVerdict.Bound(
                assetId = matchedAsset.id,
                assetDigest = provenDigest,
                releasePublishedAt = matchedRelease.publishedAt,
            )
        } else {
            UpdateVerdict.Bound(
                assetId = app.installedAssetId,
                assetDigest = storedDigest,
                releasePublishedAt = installedPublishedAt,
            )
        }
    return ResolvedBinding(bound = bound)
}

@Suppress("LongParameterList")
private suspend fun confirmLegacyBinding(
    app: InstalledAppEntity,
    matchedRelease: GithubRelease,
    matchedDigest: String,
    installedPublishedAt: String?,
    info: SystemPackageInfo?,
    verifier: DigestVerifier,
): String? {
    val confirmation =
        DigestConfirmationInput(
            installedAssetId = app.installedAssetId,
            installedAssetDigest = app.installedAssetDigest,
            installedVersion = app.installedVersion,
            matchedTag = matchedRelease.tagName,
            matchedDigest = matchedDigest,
            matchedPublishedAt = matchedRelease.publishedAt,
            installedPublishedAt = installedPublishedAt,
        )
    val path = info?.apkPath
    return when {
        !shouldConfirmDigest(confirmation) -> null
        path == null -> null
        else -> runCatching { verifier.computeSha256(path) }.getOrNull()
    }
}

internal suspend fun rebindProvenIdentity(
    app: InstalledAppEntity,
    matchedRelease: GithubRelease,
    matchedAsset: GithubAsset,
    matchedDigest: String,
    dao: InstalledAppDao,
) {
    val identityCurrent =
        app.installedAssetDigest == matchedDigest &&
            app.installedAssetId == matchedAsset.id &&
            app.installedReleaseId == matchedRelease.id &&
            app.installedVersion == matchedRelease.tagName &&
            app.installedReleasePublishedAt == matchedRelease.publishedAt
    if (identityCurrent) return
    runCatching {
        dao.adoptMatchedTagWithIdentity(
            packageName = app.packageName,
            installedVersion = matchedRelease.tagName,
            installedVersionName = app.installedVersionName,
            installedVersionCode = app.installedVersionCode,
            releaseId = matchedRelease.id,
            assetId = matchedAsset.id,
            digest = matchedDigest,
            publishedAt = matchedRelease.publishedAt,
        )
    }.onFailure { error ->
        Logger.w(error) { "Failed to rebind the proven identity for ${app.packageName}" }
    }
}

// The decision is a pure function so the trigger rules are testable without a device.
internal data class DigestConfirmationInput(
    val installedAssetId: Long?,
    val installedAssetDigest: String?,
    val installedVersion: String,
    val matchedTag: String,
    val matchedDigest: String?,
    val matchedPublishedAt: String?,
    val installedPublishedAt: String?,
)

internal fun shouldConfirmDigest(input: DigestConfirmationInput): Boolean =
    input.installedAssetDigest == null &&
        input.installedAssetId != null &&
        input.matchedDigest != null &&
        VersionMath.isExactSameVersion(input.matchedTag, input.installedVersion) &&
        VersionMath.publishedAtAfter(input.matchedPublishedAt, input.installedPublishedAt)

// When the digest is already proven equal, adoption also refreshes the release identity so the
// tag and the four identity fields can never describe different releases.
internal suspend fun adoptTagWhenProven(
    dao: InstalledAppDao,
    app: InstalledAppEntity,
    matchedRelease: GithubRelease,
    matchedAsset: GithubAsset,
    isUpdateAvailable: Boolean,
) {
    val digestProven =
        app.installedAssetDigest != null &&
            matchedAsset.digest != null &&
            app.installedAssetDigest == matchedAsset.digest
    if (digestProven && matchedAsset.digest != null) {
        dao.adoptMatchedTagWithIdentity(
            packageName = app.packageName,
            installedVersion = matchedRelease.tagName,
            installedVersionName = app.installedVersionName,
            installedVersionCode = app.installedVersionCode,
            releaseId = matchedRelease.id,
            assetId = matchedAsset.id,
            digest = matchedAsset.digest,
            publishedAt = matchedRelease.publishedAt,
        )
    } else {
        dao.adoptMatchedTag(
            app = app,
            matchedTag = matchedRelease.tagName,
            isUpdateAvailable = isUpdateAvailable,
        )
    }
}

// Adoption may rewrite the tag only when the stored identity does not contradict the release.
internal fun identityContradicts(
    app: InstalledAppEntity,
    matchedAsset: GithubAsset,
): Boolean =
    (
        app.installedAssetDigest != null &&
            matchedAsset.digest != null &&
            app.installedAssetDigest != matchedAsset.digest
    ) ||
        (
            app.installedAssetDigest == null &&
                app.installedAssetId != null &&
                app.installedAssetId != matchedAsset.id
        )

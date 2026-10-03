package zed.rainxch.core.domain.model.installation

import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.core.domain.utils.VersionVerdict
import zed.rainxch.core.domain.utils.resolveExternalInstallVerdict

fun InstalledApp.confirmInstall(
    tag: String,
    assetName: String,
    assetUrl: String,
    versionName: String,
    versionCode: Long,
    signingFingerprint: String?,
    at: Long,
    isPending: Boolean = false,
): InstalledApp {
    val timestampTrackedTag = VersionMath.isTimestampTrackedTag(tag)
    val previousSnapshotCode = latestVersionCode

    val installedSide =
        versionName.takeIf {
            it.isNotBlank() && VersionMath.versionsReconcilable(latestVersion, it)
        } ?: tag
    val targetVersionStillNewer =
        !latestVersion.isNullOrBlank() &&
            VersionMath.isVersionNewer(latestVersion, installedSide)
    val landedCodeBelowTarget =
        latestVersionCode != null && latestVersionCode > 0L && versionCode < latestVersionCode
    val isUpdateStillAvailable = targetVersionStillNewer || landedCodeBelowTarget
    val latestIsSkipped = VersionMath.isExactSameVersion(latestVersion, skippedReleaseTag)

    val parkedFile = if (isPending) pendingInstallFilePath else null
    val parkedVersion = if (isPending) pendingInstallVersion else null
    val parkedAsset = if (isPending) pendingInstallAssetName else null

    return copy(
        installedVersion = tag,
        installedAssetName = assetName,
        installedAssetUrl = assetUrl,
        installedVersionName = versionName,
        installedVersionCode = versionCode,
        isUpdateAvailable =
            when {
                latestIsSkipped -> false
                !timestampTrackedTag -> isUpdateStillAvailable
                landedCodeBelowTarget -> true
                else -> false
            },
        latestVersionCode =
            if (timestampTrackedTag || isUpdateStillAvailable) previousSnapshotCode else versionCode,
        isPendingInstall = isPending,
        lastUpdatedAt = at,
        lastCheckedAt = at,
        signingFingerprint = signingFingerprint,
        pendingInstallFilePath = parkedFile,
        pendingInstallVersion = parkedVersion,
        pendingInstallAssetName = parkedAsset,
    )
}

fun InstalledApp.resolvePendingFromSystem(
    resolvedTag: String,
    versionName: String?,
    versionCode: Long,
): InstalledApp {
    val targetCode = latestVersionCode ?: 0L
    val installReachedTarget = targetCode > 0L && versionCode >= targetCode
    val adoptedTag =
        if (installReachedTarget) {
            pendingInstallVersion ?: resolvedTag
        } else {
            installedVersion
        }
    return copy(
        isPendingInstall = false,
        installedVersion = adoptedTag,
        installedVersionName = versionName,
        installedVersionCode = versionCode,
        isUpdateAvailable = updateFlagAgainstSnapshot(versionCode, versionName ?: adoptedTag),
    )
}

private fun InstalledApp.updateFlagAgainstSnapshot(
    installedCode: Long,
    installedVersion: String?,
): Boolean {
    val snapshotCode = latestVersionCode
    if (snapshotCode != null && snapshotCode > 0L) return snapshotCode > installedCode
    return VersionMath.isVersionNewer(latestVersion, installedVersion)
}

fun InstalledApp.snapshotStillNamesNewerBuild(
    installedCode: Long,
    installedVersion: String?,
): Boolean = updateFlagAgainstSnapshot(installedCode, installedVersion)

fun InstalledApp.externalInstallUpdateFlag(
    newVersionName: String,
    newVersionCode: Long,
): Boolean =
    when (resolveExternalInstallVerdict(this, newVersionName, newVersionCode)) {
        VersionVerdict.UP_TO_DATE -> false
        VersionVerdict.UPDATE_AVAILABLE -> true
        VersionVerdict.UNKNOWN -> isUpdateAvailable
    }

fun InstalledApp.normalizeInstalledTag(
    tag: String,
    isUpdateAvailable: Boolean,
): InstalledApp = copy(
    installedVersion = tag,
    isUpdateAvailable = isUpdateAvailable,
)

fun InstalledApp.withMigratedVersionInfo(
    versionName: String?,
    versionCode: Long,
): InstalledApp = copy(
    installedVersionName = versionName,
    installedVersionCode = versionCode,
    latestVersionName = if (versionCode > 0L) versionName else latestVersionName,
    latestVersionCode = if (versionCode > 0L) versionCode else latestVersionCode,
)

fun InstalledApp.tagForObservedBuild(
    versionName: String?,
    versionCode: Long,
): String {
    val snapshotTag = latestVersion?.takeIf { it.isNotBlank() } ?: return installedVersion
    val codeProvesSnapshot =
        latestVersionCode != null && latestVersionCode > 0L && versionCode == latestVersionCode
    val nameProvesSnapshot =
        versionName != null &&
            VersionMath.versionsReconcilable(versionName, snapshotTag) &&
            VersionMath.isSameVersion(versionName, snapshotTag)
    return if (codeProvesSnapshot || nameProvesSnapshot) snapshotTag else installedVersion
}

fun InstalledApp.observeExternalInstall(
    versionName: String?,
    versionCode: Long,
): InstalledApp {
    val adoptedTag = tagForObservedBuild(versionName, versionCode)
    return copy(
        installedVersion = adoptedTag,
        installedVersionName = versionName,
        installedVersionCode = versionCode,
        isUpdateAvailable = updateFlagAgainstSnapshot(versionCode, versionName ?: adoptedTag),
    )
}

fun InstalledApp.markPending(): InstalledApp = copy(isPendingInstall = true)

fun InstalledApp.clearPending(): InstalledApp = copy(isPendingInstall = false)

fun InstalledApp.withLatestSnapshot(
    version: String,
    assetName: String?,
    assetUrl: String?,
    versionName: String?,
    versionCode: Long?,
): InstalledApp = copy(
    latestVersion = version,
    latestAssetName = assetName,
    latestAssetUrl = assetUrl,
    latestVersionName = versionName,
    latestVersionCode = versionCode,
)

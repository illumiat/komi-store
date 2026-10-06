package zed.rainxch.core.domain.model.installation

import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.core.domain.utils.VersionVerdict
import zed.rainxch.core.domain.utils.resolveExternalInstallVerdict

fun InstalledApp.confirmInstall(
    tag: String,
    releaseId: Long? = null,
    assetId: Long? = null,
    assetDigest: String? = null,
    // Unknown must be null, never "": "" never equals a real asset name.
    assetName: String?,
    assetUrl: String?,
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
        installedReleaseId = releaseId,
        installedAssetId = assetId,
        installedAssetDigest = assetDigest,
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
        pendingInstallReleaseId = null,
        pendingInstallAssetId = null,
        pendingInstallAssetDigest = null,
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
    return withSettledInstallIdentity(versionCode).copy(
        isPendingInstall = false,
        installedVersion = adoptedTag,
        installedVersionName = versionName,
        installedVersionCode = versionCode,
        isUpdateAvailable = updateFlagAgainstSnapshot(versionCode, versionName ?: adoptedTag),
    )
}

fun InstalledApp.withSettledInstallIdentity(versionCode: Long): InstalledApp {
    val hasParkedIdentity =
        pendingInstallReleaseId != null || pendingInstallAssetId != null || pendingInstallAssetDigest != null
    val targetCode = latestVersionCode ?: 0L
    val parkedBuildLanded = hasParkedIdentity && targetCode > 0L && versionCode == targetCode
    val recordedBuildStands = !parkedBuildLanded && versionCode == installedVersionCode
    return copy(
        installedReleaseId =
            when {
                parkedBuildLanded -> pendingInstallReleaseId
                recordedBuildStands -> installedReleaseId
                else -> null
            },
        installedAssetId =
            when {
                parkedBuildLanded -> pendingInstallAssetId
                recordedBuildStands -> installedAssetId
                else -> null
            },
        installedAssetDigest =
            when {
                parkedBuildLanded -> pendingInstallAssetDigest
                recordedBuildStands -> installedAssetDigest
                else -> null
            },
        pendingInstallReleaseId = null,
        pendingInstallAssetId = null,
        pendingInstallAssetDigest = null,
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

sealed interface BindingStatus {
    data object Intact : BindingStatus

    data class Broken(val reason: BreakReason) : BindingStatus

    enum class BreakReason {
        PACKAGE_NAME,

        VERSION_CODE,

        VERSION_NAME,

        SIGNING_FINGERPRINT,
    }
}

/** What a scan of the device found relative to the record. */
enum class DeviceChange {
    /** Everything the record knows still matches: there is nothing to write. */
    NONE,

    DOWNGRADE,

    /**
     * Same version, different signer. The version fields cannot express this, and reporting it as
     * an "update" names a version change that did not happen.
     */
    SIGNER_CHANGE,

    VERSION_CHANGE,
}

/**
 * Compares the record against the device.
 *
 * A drift is only claimed when *both* sides carry a key — a value we do not have cannot disagree,
 * and guessing would turn every unknown into a false alarm.
 *
 * [SIGNER_CHANGE] is deliberately not [NONE]: a signer that differs from the recorded one has to
 * keep the record out of the "nothing to do" branch, or nothing would ever write the device's key
 * back and the same mismatch would be reported on every scan.
 */
fun InstalledApp.deviceChangeAgainst(local: SystemPackageInfo): DeviceChange {
    val signerDrifted =
        !local.signingFingerprint.isNullOrBlank() &&
            !signingFingerprint.isNullOrBlank() &&
            !local.signingFingerprint.equals(signingFingerprint, ignoreCase = true)
    val versionMatches =
        local.versionCode == installedVersionCode && local.versionName == installedVersionName
    return when {
        versionMatches && !signerDrifted -> DeviceChange.NONE
        local.versionCode < installedVersionCode -> DeviceChange.DOWNGRADE
        versionMatches -> DeviceChange.SIGNER_CHANGE
        else -> DeviceChange.VERSION_CHANGE
    }
}

fun InstalledApp.bindingStatusAgainst(local: SystemPackageInfo): BindingStatus {
    if (local.packageName != packageName) {
        return BindingStatus.Broken(BindingStatus.BreakReason.PACKAGE_NAME)
    }

    if (local.versionCode > 0L &&
        installedVersionCode > 0L &&
        local.versionCode != installedVersionCode
    ) {
        return BindingStatus.Broken(BindingStatus.BreakReason.VERSION_CODE)
    }

    if (local.versionName.isNotBlank() &&
        !installedVersionName.isNullOrBlank() &&
        local.versionName != installedVersionName
    ) {
        return BindingStatus.Broken(BindingStatus.BreakReason.VERSION_NAME)
    }

    // The one criterion the version fields cannot express: a different signer is a different build.
    val localSign = local.signingFingerprint
    if (!localSign.isNullOrBlank() &&
        !signingFingerprint.isNullOrBlank() &&
        !localSign.equals(signingFingerprint, ignoreCase = true)
    ) {
        return BindingStatus.Broken(BindingStatus.BreakReason.SIGNING_FINGERPRINT)
    }

    return BindingStatus.Intact
}

// Not our release, and which one it is is unknown.
fun InstalledApp.observeExternalInstall(
    versionName: String?,
    versionCode: Long,
    signingFingerprint: String? = null,
): InstalledApp {
    val adoptedTag = tagForObservedBuild(versionName, versionCode)
    return copy(
        installedVersion = adoptedTag,
        installedVersionName = versionName,
        installedVersionCode = versionCode,
        signingFingerprint = signingFingerprint ?: this.signingFingerprint,
        installedReleaseId = null,
        installedAssetId = null,
        installedAssetDigest = null,
        isUpdateAvailable = updateFlagAgainstSnapshot(versionCode, versionName ?: adoptedTag),
    )
}

fun InstalledApp.markPending(
    releaseId: Long?,
    assetId: Long?,
    assetDigest: String?,
): InstalledApp = copy(
    isPendingInstall = true,
    pendingInstallReleaseId = releaseId,
    pendingInstallAssetId = assetId,
    pendingInstallAssetDigest = assetDigest,
)

fun InstalledApp.clearPending(): InstalledApp = copy(
    isPendingInstall = false,
    pendingInstallReleaseId = null,
    pendingInstallAssetId = null,
    pendingInstallAssetDigest = null,
)

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

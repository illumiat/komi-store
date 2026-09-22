package zed.rainxch.core.domain.model.installation

import zed.rainxch.core.domain.utils.VersionMath

// Zone-scoped write surface for InstalledApp. A bare copy() with dozens of
// named args let any writer overwrite fields owned by another writer (the
// overwrite-bug class); each function below copies the fields of its declared
// zone, pinned by InstalledAppUpdatesTest. withLatestSnapshot copies only the
// install-target subset of the check zone (version name/asset); the remaining
// check-zone fields latestAssetSize and latestReleasePublishedAt are owned by the
// scan path (InstalledAppsRepositoryImpl.updateVersionInfo), not by this write
// surface, so "its declared zone" below means each zone's integration fields, not
// every field the zone owns. Three declared cross-side owners: the migrate zone
// (one-time import normalizer owning both sides' version name/code, never tags,
// flags, or assets), confirmInstall reconciling latestVersionCode to the installed
// code — but only when the landed build actually reached the update target;
// otherwise the target is left intact — and observeExternalInstall adopting the
// snapshot tag once the observed code proves the package is the snapshot build
// (see the observe zone's own note).

// install zone — real install/confirm events only

// isUpdateAvailable recomputed against the stored latest snapshot; pending
// metadata cleared unless the install hands off to the system installer.
fun InstalledApp.confirmInstall(
    tag: String,
    assetName: String,
    assetUrl: String,
    versionName: String,
    versionCode: Long,
    signingFingerprint: String?,
    isPending: Boolean = false,
    at: Long,
): InstalledApp {
    // Writes only the install zone (see the header). The red dot is judged
    // against the build that actually landed, not the tag we asked the installer
    // for: `versionCode`/`versionName` come from the device package info, while
    // `tag` names the requested release. When they disagree — the system installed
    // an older build than the target — the flag must survive and the target's
    // versionCode must not be reconciled downward, or the update could never be
    // offered again.
    //
    // `versionName` is the truer installed side whenever it is numerically
    // comparable to the target version; for opaque markers such as `nightly` the
    // versionName is the APK's numeric version (e.g. "26.09.01") and the only
    // comparable name is the requested release tag.
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

    // Non-pending confirmations finish the install, so pending metadata goes
    // with them; a system-installer handoff keeps the parked file alive.
    val parkedFile = if (isPending) pendingInstallFilePath else null
    val parkedVersion = if (isPending) pendingInstallVersion else null
    val parkedAsset = if (isPending) pendingInstallAssetName else null

    return copy(
        installedVersion = tag,
        installedAssetName = assetName,
        installedAssetUrl = assetUrl,
        installedVersionName = versionName,
        installedVersionCode = versionCode,
        isUpdateAvailable = isUpdateStillAvailable,
        latestVersionCode = if (isUpdateStillAvailable) latestVersionCode else versionCode,
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
): InstalledApp = copy(
    isPendingInstall = false,
    installedVersion = resolvedTag,
    installedVersionName = versionName,
    installedVersionCode = versionCode,
    isUpdateAvailable = updateFlagAgainstSnapshot(versionCode),
)

// an installed code below the stored snapshot means an update is still on
// the table; a null snapshot means nothing newer is known
private fun InstalledApp.updateFlagAgainstSnapshot(installedCode: Long): Boolean =
    (latestVersionCode ?: 0L) > installedCode

// only valid when the system confirms the installed code already matches
fun InstalledApp.normalizeInstalledTag(tag: String): InstalledApp = copy(
    installedVersion = tag,
    isUpdateAvailable = false,
)

// one-time import/migration normalization; owns both sides' version fields by design
fun InstalledApp.withMigratedVersionInfo(
    versionName: String?,
    versionCode: Long,
): InstalledApp = copy(
    installedVersionName = versionName,
    installedVersionCode = versionCode,
    latestVersionName = versionName,
    latestVersionCode = versionCode,
)

// observe zone — system observations; the installed tag is adopted only where the
// observed code proves the package is the snapshot build (see below)

fun InstalledApp.observeExternalInstall(
    versionName: String?,
    versionCode: Long,
): InstalledApp {
    // A system observation normally refreshes only what the system can see
    // (versionName/versionCode). It may also adopt the installed tag, but only on
    // evidence that the package is the build the snapshot pairs that tag with: the
    // observed code equals the snapshot's code — the same equality UpdateVerdict uses
    // for "the package really is that build". A package above the snapshot is some
    // later build the snapshot tag does not name, and one with no positive snapshot
    // code has nothing to be measured against, so in both the tag is left alone.
    val snapshotTag = latestVersion
    val isSnapshotBuild =
        latestVersionCode != null &&
            latestVersionCode > 0L &&
            versionCode == latestVersionCode &&
            !snapshotTag.isNullOrBlank()
    val adoptedTag =
        if (isSnapshotBuild && snapshotTag != null) snapshotTag else installedVersion
    return copy(
        installedVersion = adoptedTag,
        installedVersionName = versionName,
        installedVersionCode = versionCode,
        isUpdateAvailable = updateFlagAgainstSnapshot(versionCode),
    )
}

// pending zone

fun InstalledApp.markPending(): InstalledApp = copy(isPendingInstall = true)

fun InstalledApp.clearPending(): InstalledApp = copy(isPendingInstall = false)

// check zone — install-target snapshot only

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

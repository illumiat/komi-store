package zed.rainxch.core.domain.model.installation

import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.core.domain.utils.VersionVerdict
import zed.rainxch.core.domain.utils.resolveExternalInstallVerdict

// Zone-scoped write surface for InstalledApp. A bare copy() with dozens of
// named args let any writer overwrite fields owned by another writer (the
// overwrite-bug class); each function below copies the fields of its declared
// zone, pinned by InstalledAppUpdatesTest. withLatestSnapshot copies only the
// install-target subset of the check zone (version name/asset); the remaining
// check-zone fields latestAssetSize and latestReleasePublishedAt are owned by the
// scan path (InstalledAppsRepositoryImpl.updateVersionInfo), not by this write
// surface, so "its declared zone" below means each zone's integration fields, not
// every field the zone owns. withSkippedRelease is the skip zone's only writer, and
// the repository chains it after confirmInstall so the build that landed and the
// skip decision that came with it reach the record in one write.
// Four declared cross-side owners: the migrate zone
// (one-time import normalizer owning both sides' version name/code, never tags,
// flags, or assets), confirmInstall reconciling latestVersionCode to the installed
// code — but only when the landed build actually reached the update target, and never
// for a timestamp-tracked tag, whose snapshot is the only evidence the timestamp
// branch has; otherwise the target is left intact — withSkippedRelease bringing the
// install zone's update flag down when the skip it writes names the release the
// snapshot still points at (a skip is the user's decision and outranks the recomputed
// flag), and observeExternalInstall
// adopting the snapshot tag once the observed code proves the package is the snapshot
// build (see the observe zone's own note).

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
    at: Long,
    isPending: Boolean = false,
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
    //
    // Timestamp-tracked tags (`nightly`, a commit-hash tail) are outside the code
    // judgement above: one tag names many builds, so `isVersionNewer(tag, tag)` is always
    // false and a landed code equal to the snapshot code proves nothing either. What is
    // left to decide is the flag, and here the install does decide it, because the flag
    // is not a record of what the codes said — it is the timestamp branch's memory.
    //
    // While that flag is up, a scan of the same tag at the same publish time keeps
    // reporting the release (see shouldReportTimestampUpdate, pinned by
    // "timestamp_update_retained_across_scans_without_install"). That is its purpose:
    // it holds an un-acted-on update on screen across scans, since publish time alone
    // cannot tell a rebuilt release from the one already stored. Installing is the act
    // that resolves it, so it must not outlive the install. Left standing, it re-reports
    // the very release the package now is on every later scan, for as long as it takes
    // the next build to be published — "you installed the nightly" rendered as "an
    // update is available". The verdict side has always assumed the flag comes down
    // here: "timestamp_update_not_reported_after_install" asserts silence for exactly
    // this release and this publish time once it is down.
    //
    // The snapshot code is not the flag's to clear — it stays at previousSnapshotCode
    // below, and it is the evidence the timestamp branch compares publish times against.
    // Only the latch comes down, and it stays up in the one case where the landed code
    // proves the install did not reach the snapshot: an older build went on, so the
    // update is still waiting to be taken.
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
        isUpdateAvailable =
            when {
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
    // Which tag the finished install carries. The parked tag names the exact release
    // handed to the installer and outranks latestVersion/versionName, which a
    // checkForUpdates during the install window may have moved on — otherwise a build
    // installed early gets stamped with a newer name and the next sameTag comparison
    // reads equal, silently dropping the still-pending update.
    //
    // The parked/target tag is adopted only when the system code proves that release
    // actually landed. When the user cancels the system dialog (or the install fails
    // silently) systemInfo still describes the old package, and stamping the target tag
    // would leave installedVersionCode on the old code while installedVersion claims the
    // target — the same sameTag lock, now permanent. Below target the old tag is kept
    // and only the pending flag is cleared. Same criterion the callers apply when they
    // choose resolvedTag.
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

// an installed code below the stored snapshot means an update is still on
// the table. A null or zero snapshot code carries no ordering — reading it as
// "0 is not above the installed code" hid a real update behind a stale badge —
// so fall back to the version names, where an uncomparable side degrades to
// "not newer" rather than a false negative.
private fun InstalledApp.updateFlagAgainstSnapshot(
    installedCode: Long,
    installedVersion: String?,
): Boolean {
    val snapshotCode = latestVersionCode
    if (snapshotCode != null && snapshotCode > 0L) return snapshotCode > installedCode
    return VersionMath.isVersionNewer(latestVersion, installedVersion)
}

// The snapshot-vs-installed comparison behind the flag, exposed so the
// PackageEventReceiver path (a replace that fell short of the parked target) can reuse
// it instead of duplicating the ordering. Public rather than internal because that
// caller lives in core/data, a different module, where an internal declaration of
// core/domain is not visible.
fun InstalledApp.snapshotStillNamesNewerBuild(
    installedCode: Long,
    installedVersion: String?,
): Boolean = updateFlagAgainstSnapshot(installedCode, installedVersion)

// The verdict of an observed external install, expressed as the update flag the stored
// snapshot now justifies. It compares the package to the snapshot by versionCode first
// and version name second, and deliberately never through the stored installed tag:
// after an external install that tag is stale, and re-deriving from it
// (isVersionNewer(staleTag, matchedTag)) is exactly what re-raised a flag the
// observation had just cleared. The PackageEventReceiver backstop replays this after
// its checkForUpdates for that reason.
fun InstalledApp.externalInstallUpdateFlag(
    newVersionName: String,
    newVersionCode: Long,
): Boolean =
    when (resolveExternalInstallVerdict(this, newVersionName, newVersionCode)) {
        VersionVerdict.UP_TO_DATE -> false
        VersionVerdict.UPDATE_AVAILABLE -> true
        VersionVerdict.UNKNOWN -> isUpdateAvailable
    }

// Adopting the tag is only valid once the system confirms the installed code already
// matches that build, so the flag is forwarded from the verdict the caller reached —
// exactly as adoptMatchedTag does — instead of being recomputed here. Hard-coding
// `false` would wipe a correct "update available": for a tag that names many builds
// (nightly, a hash tail) the same code can be a new build, and the stored true is the
// right timestamp verdict for it.
fun InstalledApp.normalizeInstalledTag(
    tag: String,
    isUpdateAvailable: Boolean,
): InstalledApp = copy(
    installedVersion = tag,
    isUpdateAvailable = isUpdateAvailable,
)

// one-time import/migration normalization; owns both sides' version fields by design.
// The check-zone snapshot (latestVersionName/Code) is only rewritten when the migrated
// code is positive: the fallback migration path reports code 0L, and copying that onto
// the snapshot would contradict it — a later `0 > 0` comparison and the `> 0L` guards
// would both misfire against a snapshot that holds a real version name.
fun InstalledApp.withMigratedVersionInfo(
    versionName: String?,
    versionCode: Long,
): InstalledApp = copy(
    installedVersionName = versionName,
    installedVersionCode = versionCode,
    latestVersionName = if (versionCode > 0L) versionName else latestVersionName,
    latestVersionCode = if (versionCode > 0L) versionCode else latestVersionCode,
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
        isUpdateAvailable = updateFlagAgainstSnapshot(versionCode, versionName ?: adoptedTag),
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

// skip zone — the release the user asked to be left alone

// A skip is keyed by tag, so the tag written here is the one a check must not offer
// again, and null is how a skip is released. The repository chains this straight after
// confirmInstall, which is what keeps the build that landed and the skip decision that
// came with it in one write: a check that read the new installed tag without the
// matching skip would offer back the release the install just superseded.
//
// It is also a declared cross-side owner of the install zone's flag, and that is the
// point of it existing at all. confirmInstall recomputes the flag from the record's own
// snapshot, which is right for an install and wrong for a skip: the snapshot still
// names the release the update flag is drawn from, so a check that follows would keep
// offering it. The skip is the user's decision and has to win, so when the tag written
// here is the release the snapshot names, the flag comes down with it. A skip can only
// suppress the flag, never raise it.
//
// The caller owns whether a skip is legitimate at all — a tag that names several builds
// is not (the next build under that tag deserves to be shown), and updateAppVersion is
// where that is judged. This stays a plain override so the rule lives in one place
// rather than being half-enforced here.
fun InstalledApp.withSkippedRelease(skippedReleaseTag: String?): InstalledApp =
    copy(
        skippedReleaseTag = skippedReleaseTag,
        isUpdateAvailable =
            if (VersionMath.isExactSameVersion(latestVersion, skippedReleaseTag)) {
                false
            } else {
                isUpdateAvailable
            },
    )

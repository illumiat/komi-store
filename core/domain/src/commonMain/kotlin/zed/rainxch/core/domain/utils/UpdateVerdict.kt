package zed.rainxch.core.domain.utils

// Pure update-decision logic — no repository, no DAO, no IO. Branch order:
// a skip that still names the declined build wins over everything, then
// timestamp, then code equality, then reconcilability, then semver. Every
// branch is pinned by UpdateVerdictTest.
object UpdateVerdict {

    data class Installed(
        val tag: String?,
        val versionCode: Long,
    )

    data class Stored(
        val latestTag: String?,
        val latestVersionCode: Long?,
        val publishedAt: String?,
        val wasUpdateAvailable: Boolean,
        // The asset the baseline was taken from: its digest is the build's identity
        // for a tag that names many builds, and its size is the fallback for hosts
        // that supply no digest. See VersionMath.assetIdentityChanged.
        val latestAssetDigest: String? = null,
        val latestAssetSize: Long? = null,
    )

    data class Matched(
        val tag: String,
        val publishedAt: String?,
        val isPrerelease: Boolean,
        val assetDigest: String? = null,
        val assetSize: Long? = null,
    )

    fun decide(
        installed: Installed,
        stored: Stored,
        matched: Matched,
        skippedTag: String?,
    ): Result {
        val reconcilable = VersionMath.versionsReconcilable(installed.tag, matched.tag)
        val codesAlreadyMatch =
            installed.versionCode > 0L &&
                stored.latestVersionCode != null &&
                stored.latestVersionCode > 0L &&
                installed.versionCode == stored.latestVersionCode &&
                VersionMath.isExactSameVersion(matched.tag, stored.latestTag)

        val matchesSkipped =
            skippedTag != null && VersionMath.isExactSameVersion(matched.tag, skippedTag)
        // A skip is keyed by tag, so it can only be asked of tags where one name is one build.
        // Where the tag is timestamp-tracked instead — opaque markers such as `nightly`, and
        // unparseable hash tails, per isTimestampTrackedTag — the same name legitimately comes
        // back as a different build; that is the case the timestamp logic was added for. Once it
        // does, the release the user declined is gone, so the skip has served its purpose and must
        // not go on holding back the build that replaced it.
        //
        // A stored baseline is required, deliberately: with nothing to compare against there is
        // no evidence of a rebuild, and reading "no baseline" as "new build" would drop the skip
        // in the very check that recorded it.
        //
        // Releasing the skip only ever returns the tag to the treatment it gets when it was never
        // skipped, so this cannot be worse than not skipping at all.
        //
        // codesAlreadyMatch is deliberately NOT consulted here, even though it reads like "the
        // package already is this build". confirmInstall reconciles stored.latestVersionCode to
        // the code it just installed, and a reused tag keeps its name across builds, so for every
        // later build of that tag `installed.code == stored.code` still holds while the package is
        // in fact the earlier build. Gating on it therefore turned "skip this build" into "skip
        // this tag for good" — the exact lock this route exists to break. What separates one build
        // of a reused tag from the next is its publish time, and that is what decides below.
        //
        // Absolute-time compare, not string order: publishedAt may be laid down
        // either as UTC ("…Z") or with a numeric offset (Forgejo/Codeberg), and
        // those spell the same instant differently (see VersionMath).
        val publishedAtAdvanced =
            VersionMath.isPublishedAtAfter(matched.publishedAt, stored.publishedAt)
        val skipSupersededByNewBuild =
            matchesSkipped &&
                VersionMath.isTimestampTrackedTag(matched.tag) &&
                publishedAtAdvanced
        // Everything else keeps its skip: a release under a plain tag stays skipped until a
        // strictly newer tag arrives, and a reused tag whose publish time has not moved names the
        // very build the user declined, so it is not offered back.
        val skipHolds = matchesSkipped && !skipSupersededByNewBuild
        val skipBecameStale =
            skipSupersededByNewBuild ||
                (skippedTag != null &&
                    !matchesSkipped &&
                    // isVersionNewer degrades to a string compare on tags it cannot parse
                    // (hash tails, opaque markers), where a lexicographically larger name
                    // does not mean a newer build — "26.08.11f15e4" sorts below
                    // "26.08.21fae85". Those tags are time-tracked; only a plain,
                    // comparable tag names a single build an ordering can supersede.
                    !VersionMath.isTimestampTrackedTag(matched.tag) &&
                    !VersionMath.isTimestampTrackedTag(skippedTag) &&
                    VersionMath.isVersionNewer(matched.tag, skippedTag))

        // The tag decides on its own whether publish time is the right question, and it decides
        // before the pre-release flag is consulted. A tag tracked by publish time — an opaque
        // marker (nightly) or a numeric prefix carrying a commit hash tail (`26.08.11f15e4`) —
        // is asked for its publish time whether or not the release carries GitHub's pre-release
        // flag: the flag records how the maintainer filed the release, the tag records that its
        // version number names a build rather than an ordering. Reading the flag first left every
        // unflagged hash tail comparing as plain strings, where `26.08.11f15e4` sorts below the
        // installed `26.08.21fae85` and the newer build was announced as nothing at all.
        val timestampTracked = VersionMath.isTimestampTrackedTag(matched.tag)
        val sameTag = VersionMath.isExactSameVersion(matched.tag, installed.tag)
        val usedTimestampLogic =
            timestampTracked ||
                (sameTag && !reconcilable) ||
                (!reconcilable && (matched.isPrerelease || VersionMath.isPreReleaseTag(matched.tag)))

        val timestampWouldReport =
            if (usedTimestampLogic) {
                VersionMath.shouldReportTimestampUpdate(
                    matchedTag = matched.tag,
                    matchedPublishedAt = matched.publishedAt,
                    previousLatestPublishedAt = stored.publishedAt,
                    previousWasUpdateAvailable = stored.wasUpdateAvailable,
                    previousLatestTag = stored.latestTag,
                    matchedAssetDigest = matched.assetDigest,
                    matchedAssetSize = matched.assetSize,
                    previousAssetDigest = stored.latestAssetDigest,
                    previousAssetSize = stored.latestAssetSize,
                )
            } else {
                false
            }

        val isUpdateAvailable =
            when {
                // A release the user deliberately skipped is not re-offered. It is released
                // again by a strictly newer tag, or — for a tag that names many builds — by the
                // same tag carrying a newer build, which is what `skipSupersededByNewBuild`
                // detects. Without that second route an opaque tag such as `nightly` could never
                // become due again, and skipping one build would quietly turn into ignoring the
                // app's updates for good.
                skipHolds -> false
                usedTimestampLogic -> timestampWouldReport
                codesAlreadyMatch -> false
                !reconcilable -> false
                else ->
                    VersionMath.isVersionNewer(
                        candidate = matched.tag,
                        current = installed.tag,
                    )
            }

        return Result(
            isUpdateAvailable = isUpdateAvailable,
            skipBecameStale = skipBecameStale,
            codesAlreadyMatch = codesAlreadyMatch,
        )
    }

    // Whether the stored installed tag may be rewritten to the matched release's tag.
    //
    // codesAlreadyMatch is the strict proof: the installed code is the code the stored
    // snapshot pairs with the matched tag, so only the tag text drifted. It is not the
    // only route, though. codesAlreadyMatch reads stored.latestVersionCode, which
    // checkForUpdates clears to null whenever the matched tag moves past the stored one
    // (see updateVersionInfo), so once a snapshot drifts the strict route stays shut for
    // good and the rewrite becomes a dead path. Rows written before tags were tracked —
    // an installedVersion that is really a versionName — are exactly the ones that need
    // the rewrite, and they are recognisable by being irreconcilable with the matched
    // tag, which names a build they cannot be. Keeping that second route lets the
    // rewrite self-heal instead of locking the tag in place for good.
    fun shouldAdoptMatchedTag(
        codesAlreadyMatch: Boolean,
        installedTag: String?,
        matchedTag: String,
    ): Boolean =
        installedTag != matchedTag &&
            (codesAlreadyMatch || !VersionMath.versionsReconcilable(installedTag, matchedTag))

    data class Result(
        val isUpdateAvailable: Boolean,
        val skipBecameStale: Boolean,
        // true when the installed APK's versionCode already equals the matched
        // release's (the package really is that build) — the strict proof that
        // rewriting the installed tag is legitimate (see shouldAdoptMatchedTag for the
        // self-healing second route)
        val codesAlreadyMatch: Boolean,
    )
}

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
    )

    data class Matched(
        val tag: String,
        val publishedAt: String?,
        val isPrerelease: Boolean,
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
                    VersionMath.isVersionNewer(matched.tag, skippedTag))

        val opaqueMatched = VersionMath.isOpaqueMarker(matched.tag)
        val sameTag = VersionMath.isExactSameVersion(matched.tag, installed.tag)
        val usedTimestampLogic =
            opaqueMatched ||
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

    data class Result(
        val isUpdateAvailable: Boolean,
        val skipBecameStale: Boolean,
        // true when the installed APK's versionCode already equals the matched
        // release's (the package really is that build) — the only case where
        // rewriting the installed tag is legitimate
        val codesAlreadyMatch: Boolean,
    )
}

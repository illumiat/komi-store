package zed.rainxch.core.domain.utils

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
        val latestReleaseId: Long? = null,
        val latestAssetId: Long? = null,
        val latestAssetDigest: String? = null,
        val latestAssetSize: Long? = null,
    )

    data class Matched(
        val tag: String,
        val publishedAt: String?,
        val isPrerelease: Boolean,
        val releaseId: Long? = null,
        val assetId: Long? = null,
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
        val publishedAtAdvanced =
            VersionMath.isPublishedAtAfter(matched.publishedAt, stored.publishedAt)
        val identityAdvanced =
            VersionMath.releaseObjectChanged(
                matchedReleaseId = matched.releaseId,
                matchedAssetId = matched.assetId,
                storedReleaseId = stored.latestReleaseId,
                storedAssetId = stored.latestAssetId,
            ) ||
                VersionMath.assetIdentityChanged(
                    matchedDigest = matched.assetDigest,
                    matchedSize = matched.assetSize,
                    storedDigest = stored.latestAssetDigest,
                    storedSize = stored.latestAssetSize,
                )
        val skipSupersededByNewBuild =
            matchesSkipped &&
                VersionMath.isTimestampTrackedTag(matched.tag) &&
                (publishedAtAdvanced || identityAdvanced)
        val skipHolds = matchesSkipped && !skipSupersededByNewBuild
        val skipBecameStale =
            skipSupersededByNewBuild ||
                (skippedTag != null &&
                    !matchesSkipped &&
                    !VersionMath.isTimestampTrackedTag(matched.tag) &&
                    !VersionMath.isTimestampTrackedTag(skippedTag) &&
                    VersionMath.isVersionNewer(matched.tag, skippedTag))

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
                    matchedReleaseId = matched.releaseId,
                    matchedAssetId = matched.assetId,
                    previousReleaseId = stored.latestReleaseId,
                    previousAssetId = stored.latestAssetId,
                    installedTag = installed.tag,
                )
            } else {
                false
            }

        val isUpdateAvailable =
            when {
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

    fun shouldAdoptMatchedTag(
        codesAlreadyMatch: Boolean,
        installedTag: String?,
        matchedTag: String,
    ): Boolean = installedTag != matchedTag && codesAlreadyMatch

    /**
     * Whether the recorded install and the matched asset are the same bytes. The same asset id
     * means the same file; a digest that both sides carry is the stronger statement, because a
     * re-upload gets a new id but keeps its bytes.
     */
    fun isSameFile(
        installedAssetId: Long?,
        installedAssetDigest: String?,
        matchedAssetId: Long?,
        matchedAssetDigest: String?,
    ): Boolean {
        if (installedAssetDigest != null && matchedAssetDigest != null) {
            return installedAssetDigest == matchedAssetDigest
        }
        return installedAssetId != null && matchedAssetId != null && installedAssetId == matchedAssetId
    }

    /**
     * The verdict for a record that carries an identity. The file decides first — the same bytes
     * are up to date whatever the tags or version strings say — and the release date decides only
     * when the file changed. When the installed release is not in the fetched window there is
     * nothing to date it against, so the caller's [fallback] stands.
     */
    fun decideBound(
        sameFile: Boolean,
        matchedPublishedAt: String?,
        installedReleasePublishedAt: String?,
        fallback: Boolean,
    ): Boolean = when {
        sameFile -> false
        installedReleasePublishedAt == null -> fallback
        else -> VersionMath.isPublishedAtAfter(matchedPublishedAt, installedReleasePublishedAt)
    }

    data class Result(
        val isUpdateAvailable: Boolean,
        val skipBecameStale: Boolean,
        val codesAlreadyMatch: Boolean,
    )
}

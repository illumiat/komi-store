package zed.rainxch.core.domain.model.repository

/**
 * Why a starred repository is stored locally but withheld from the main list.
 *
 * The sync used to drop these repositories entirely: no row, no log, no UI trace, while the
 * "Last synced" subtitle still advanced. Keeping the row with a reason turns a silent
 * omission into something the UI can explain and the user can inspect (#1040).
 */
enum class StarredExclusionReason {
    /** The repository has published no releases at all. */
    NO_RELEASES,

    /** Every release so far is a draft or a prerelease, so there is no stable version to install. */
    PRERELEASE_ONLY,

    /** A stable release exists but carries no asset for the current platform. */
    NO_MATCHING_ASSET,

    /** The check itself could not complete (network failure, rate limit, malformed response). */
    PROBE_FAILED,
}

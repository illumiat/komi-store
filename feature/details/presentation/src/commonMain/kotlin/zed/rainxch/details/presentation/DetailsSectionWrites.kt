package zed.rainxch.details.presentation

import zed.rainxch.core.domain.model.account.github.GithubUserProfile
import zed.rainxch.details.domain.model.RepoStats

// The page is assembled section by section. Each read owns its own fields and writes them the
// moment it lands; every write carries the repository it was read for, so a slow section of a page
// that has already been replaced cannot touch the new one.

internal fun RawDetailsState.withStats(
    repoId: Long,
    stats: RepoStats?,
): RawDetailsState = if (stats != null && belongsToRepo(repoId)) copy(stats = stats) else this

internal fun RawDetailsState.withUserProfile(
    repoId: Long,
    profile: GithubUserProfile?,
): RawDetailsState =
    if (profile != null && belongsToRepo(repoId)) {
        copy(userProfile = profile)
    } else {
        this
    }

internal fun RawDetailsState.withReadme(
    repoId: Long,
    markdown: String?,
    language: String?,
): RawDetailsState =
    if (markdown != null && belongsToRepo(repoId)) {
        copy(readmeMarkdown = markdown, readmeLanguage = language)
    } else {
        this
    }

// The release decision is in, so the header may show the empty or failed card now; the README,
// stats and profile can still be landing in their own writes.
internal fun RawDetailsState.withSectionsSettled(repoId: Long): RawDetailsState =
    if (belongsToRepo(repoId)) {
        copy(areSectionsPending = false, isRetryingReleases = false)
    } else {
        this
    }

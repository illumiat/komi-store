package zed.rainxch.details.presentation

import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease
import zed.rainxch.details.domain.model.ReleaseCategory

// The detail page paints the newest published release as soon as it is read, then replaces it with
// the full list in the background. These are the rules that decide how one list replaces the other,
// so the latest-first paint and the full-list refresh can never disagree about the selection.

internal data class ReleaseListChoice(
    val releases: List<GithubRelease>,
    val selected: GithubRelease,
    val category: ReleaseCategory,
)

// Null means "keep what is on screen": a failed or empty read must never clear the latest release.
internal fun chooseReleaseListChoice(
    previousSelected: GithubRelease?,
    previousCategory: ReleaseCategory,
    incoming: List<GithubRelease>?,
    failed: Boolean,
): ReleaseListChoice? {
    if (failed || incoming.isNullOrEmpty()) return null

    val carried = incoming.carriedSelection(previousSelected)
    val selected =
        carried
            ?: incoming.firstInCategory(previousCategory)
            ?: incoming.firstOrNull { !it.isEffectivelyPreRelease() }
            ?: incoming.first()
    val category =
        when {
            carried != null -> previousCategory
            selected.isEffectivelyPreRelease() -> ReleaseCategory.PRE_RELEASE
            else -> ReleaseCategory.STABLE
        }
    return ReleaseListChoice(releases = incoming, selected = selected, category = category)
}

// The user's (or the latest-first paint's) release wins as long as the new list still ships it.
private fun List<GithubRelease>.carriedSelection(previous: GithubRelease?): GithubRelease? =
    previous?.let { previousRelease ->
        firstOrNull { it.id == previousRelease.id }
            ?: firstOrNull { it.tagName == previousRelease.tagName }
    }

internal fun List<GithubRelease>.firstInCategory(category: ReleaseCategory): GithubRelease? =
    when (category) {
        ReleaseCategory.STABLE -> firstOrNull { !it.isEffectivelyPreRelease() }
        ReleaseCategory.PRE_RELEASE -> firstOrNull { it.isEffectivelyPreRelease() }
        ReleaseCategory.ALL -> firstOrNull()
    }

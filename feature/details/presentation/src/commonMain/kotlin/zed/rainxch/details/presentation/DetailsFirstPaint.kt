package zed.rainxch.details.presentation

import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.installation.InstalledApp

// The repository is the anchor of the whole screen: it is the one thing the header needs, so it
// is written first and every later write has to prove it still belongs to the same repository.
// A slow first load that loses the race to a newer one can then never replace a painted header.

internal fun RawDetailsState.withHeaderFirstPaint(
    repository: GithubRepoSummary,
    sectionsDeferred: Boolean,
): RawDetailsState =
    copy(
        isLoading = false,
        errorMessage = null,
        areSectionsPending = true,
        repository = repository,
        sectionsDeferred = sectionsDeferred,
    )

internal fun RawDetailsState.belongsToRepo(repoId: Long): Boolean = repository?.id == repoId

internal fun RawDetailsState.withFavourite(
    repoId: Long,
    isFavourite: Boolean?,
): RawDetailsState = if (belongsToRepo(repoId)) copy(isFavourite = isFavourite == true) else this

internal fun RawDetailsState.withStarred(
    repoId: Long,
    isStarred: Boolean?,
): RawDetailsState = if (belongsToRepo(repoId)) copy(isStarred = isStarred == true) else this

// The sync rewrites installed-app rows, so this is the only part of the page it may refresh.
// Releases, the README and the repository itself stay exactly as they were read.
internal fun RawDetailsState.withInstalledAppState(
    repoId: Long,
    installedApps: List<InstalledApp>,
    installedApp: InstalledApp?,
): RawDetailsState =
    if (belongsToRepo(repoId)) {
        copy(installedApps = installedApps, installedApp = installedApp)
    } else {
        this
    }

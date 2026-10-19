package zed.rainxch.starred.presentation.model

import zed.rainxch.core.domain.model.repository.StarredExclusionReason

data class StarredRepositoryUi(
    val repoId: Long,
    val repoName: String,
    val repoOwner: String,
    val repoOwnerAvatarUrl: String,
    val repoDescription: String?,
    val primaryLanguage: String?,
    val repoUrl: String,
    val stargazersCount: Int,
    val forksCount: Int,
    val openIssuesCount: Int,
    val isInstalled: Boolean,
    val isFavorite: Boolean = false,
    val isCurrentUserOwner: Boolean = false,
    val latestRelease: String?,
    val latestReleaseUrl: String?,
    val starredAt: Long?,
    /**
     * Non-null for repositories that are starred on GitHub but withheld from the main list.
     * The value is what the row explains once the hidden section is expanded (#1040).
     */
    val exclusionReason: StarredExclusionReason? = null,
)

package zed.rainxch.core.domain.model.repository

data class StarredRepository(
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
    val isInstalled: Boolean = false,
    val installedPackageName: String? = null,
    val latestVersion: String?,
    val latestReleaseUrl: String?,
    val starredAt: Long?,
    val addedAt: Long,
    val lastSyncedAt: Long,
    /**
     * Non-null when the repository is starred on GitHub but cannot be offered for install —
     * no releases, only prereleases, no asset for this platform, or the check failed. Such
     * rows are kept so the screen can report "N hidden" instead of quietly omitting them
     * (#1040).
     */
    val exclusionReason: StarredExclusionReason? = null,
)

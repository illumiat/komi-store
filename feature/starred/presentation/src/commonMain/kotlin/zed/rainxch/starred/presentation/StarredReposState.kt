package zed.rainxch.starred.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.starred.presentation.model.StarredRepositoryUi
import zed.rainxch.starred.presentation.model.StarredSortRule

data class StarredReposState(
    val starredRepositories: ImmutableList<StarredRepositoryUi> = persistentListOf(),
    val filteredRepositories: ImmutableList<StarredRepositoryUi> = persistentListOf(),
    /**
     * Starred repositories the installability check withheld, kept so the screen can explain
     * them rather than pretend the account has fewer stars (#1040).
     */
    val hiddenRepositories: ImmutableList<StarredRepositoryUi> = persistentListOf(),
    /** Whether the hidden section is expanded. */
    val isShowingHidden: Boolean = false,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val errorMessage: String? = null,
    val lastSyncTime: Long? = null,
    val lastSyncSubtitle: String? = null,
    val isAuthenticated: Boolean = false,
    val searchQuery: String = "",
    val sortRule: StarredSortRule = StarredSortRule.RecentlyStarred,
)

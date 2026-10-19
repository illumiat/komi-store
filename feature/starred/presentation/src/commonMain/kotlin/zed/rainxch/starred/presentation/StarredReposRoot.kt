package zed.rainxch.starred.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.Row
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfaceElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.presentation.components.ScrollbarContainer
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.bars.KomiTopBarSize
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.overlays.KomiDropdown
import zed.rainxch.core.presentation.components.overlays.KomiMenuItem
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.refresh.KomiPullToRefresh
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.locals.LocalScrollbarEnabled
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.utils.arrowKeyScroll
import zed.rainxch.core.domain.model.repository.StarredExclusionReason
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.starred.presentation.components.StarredRepositoryItem
import zed.rainxch.starred.presentation.model.StarredRepositoryUi
import zed.rainxch.starred.presentation.model.StarredSortRule

@Composable
fun StarredReposRoot(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (repoId: Long) -> Unit,
    onNavigateToDeveloperProfile: (username: String) -> Unit,
    onNavigateToAuthentication: () -> Unit,
    viewModel: StarredReposViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    StarredReposScreen(
        state = state,
        onAction = { action ->
            when (action) {
                StarredReposAction.OnNavigateBackClick -> onNavigateBack()
                is StarredReposAction.OnRepositoryClick -> onNavigateToDetails(action.repository.repoId)
                is StarredReposAction.OnDeveloperProfileClick -> onNavigateToDeveloperProfile(action.username)
                StarredReposAction.OnSignInClick -> onNavigateToAuthentication()
                else -> viewModel.onAction(action)
            }
        },
    )
}

@Composable
fun StarredReposScreen(
    state: StarredReposState,
    onAction: (StarredReposAction) -> Unit,
) {
    KomiScaffold(
        topBar = {
            StarredTopBar(
                subtitle = state.lastSyncSubtitle,
                isSyncing = state.isSyncing,
                sortRule = state.sortRule,
                hasRepos = state.starredRepositories.isNotEmpty(),
                onAction = onAction,
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when {
                !state.isAuthenticated -> {
                    EmptyStateContent(
                        title = stringResource(Res.string.sign_in_required),
                        message = stringResource(Res.string.sign_in_with_github_for_stars),
                        icon = Icons.Default.Star,
                        actionText = stringResource(Res.string.sign_in_with_github),
                        onActionClick = {
                            onAction(StarredReposAction.OnSignInClick)
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                state.isLoading -> {
                    KomiCircularProgress(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                state.starredRepositories.isEmpty() && !state.isSyncing -> {                    EmptyStateContent(
                        title = stringResource(Res.string.no_starred_repos),
                        message = stringResource(Res.string.star_repos_hint),
                        icon = Icons.Default.Star,
                        actionText = if (state.errorMessage != null) stringResource(Res.string.retry) else null,
                        onActionClick =
                            if (state.errorMessage != null) {
                                {
                                    onAction(StarredReposAction.OnRetrySync)
                                }
                            } else {
                                null
                            },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.starredRepositories.isNotEmpty()) {
                            StarredSearchBar(
                                query = state.searchQuery,
                                onQueryChange = { onAction(StarredReposAction.OnSearchChange(it)) },
                            )
                        }

                        KomiPullToRefresh(
                            isRefreshing = state.isSyncing,
                            onRefresh = {
                                onAction(StarredReposAction.OnRefresh)
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            val gridState = rememberLazyStaggeredGridState()
                            val isScrollbarEnabled = LocalScrollbarEnabled.current
                            ScrollbarContainer(
                                gridState = gridState,
                                enabled = isScrollbarEnabled,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                LazyVerticalStaggeredGrid(
                                    state = gridState,
                                    columns = StaggeredGridCells.Adaptive(350.dp),
                                    verticalItemSpacing = 12.dp,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(
                                        horizontal = 8.dp,
                                        vertical = 12.dp
                                    ),
                                    modifier = Modifier.fillMaxSize()
                                        .arrowKeyScroll(gridState, autoFocus = true),
                                ) {
                                    // The withheld repositories are listed here instead of
                                    // being dropped during sync, so the count and the reasons
                                    // are visible rather than implied (#1040, #784).
                                    if (state.hiddenRepositories.isNotEmpty()) {
                                        item(
                                            key = "hidden-starred-header",
                                            span = StaggeredGridItemSpan.FullLine,
                                        ) {
                                            HiddenRepositoriesHeader(
                                                count = state.hiddenRepositories.size,
                                                isExpanded = state.isShowingHidden,
                                                onClick = {
                                                    onAction(StarredReposAction.OnToggleHiddenRepos)
                                                },
                                            )
                                        }
                                    }

                                    items(
                                        items = state.filteredRepositories,
                                        key = { it.repoId },
                                    ) { repo ->
                                        StarredRepositoryItem(
                                            repository = repo,
                                            onToggleFavoriteClick = {
                                                onAction(StarredReposAction.OnToggleFavorite(repo))
                                            },
                                            onItemClick = {
                                                onAction(StarredReposAction.OnRepositoryClick(repo))
                                            },
                                            onDevProfileClick = {
                                                onAction(
                                                    StarredReposAction.OnDeveloperProfileClick(
                                                        repo.repoOwner
                                                    )
                                                )
                                            },
                                            modifier = Modifier.animateItem(),
                                        )
                                    }

                                    if (state.isShowingHidden) {
                                        items(
                                            items = state.hiddenRepositories,
                                            key = { "hidden-${it.repoId}" },
                                        ) { repo ->
                                            HiddenRepositoryRow(
                                                repository = repo,
                                                modifier = Modifier.animateItem(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            state.errorMessage?.let { message ->
                val colors = LocalPersonality.current.colors
                KomiSurface(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth(),
                    elevation = KomiSurfaceElevation.Raised,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = 8.dp
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KomiText(
                            text = message,
                            role = KomiTextRole.Body,
                            color = colors.onSurface,
                            uppercase = false,
                            modifier = Modifier.weight(1f),
                        )
                        KomiButton(
                            onClick = { onAction(StarredReposAction.OnRetrySync) },
                            label = stringResource(Res.string.retry),
                            variant = KomiButtonVariant.Text,
                            size = KomiButtonSize.Sm,
                        )
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { onAction(StarredReposAction.OnDismissError) },
                            contentAlignment = Alignment.Center,
                        ) {
                            KomiIcon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(Res.string.dismiss),
                                tint = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StarredTopBar(
    subtitle: String?,
    isSyncing: Boolean,
    sortRule: StarredSortRule,
    hasRepos: Boolean,
    onAction: (StarredReposAction) -> Unit,
) {
    val sortEntries = StarredSortRule.entries
        .map { rule ->
            KomiMenuItem(
                id = rule.name,
                label = stringResource(
                    when (rule) {
                        StarredSortRule.RecentlyStarred -> Res.string.starred_picker_sort_recent
                        StarredSortRule.NameAsc -> Res.string.starred_picker_sort_alphabetical
                        StarredSortRule.StarsDesc -> Res.string.starred_picker_sort_stars
                    }
                ),
            )
        }
        .toPersistentList()

    KomiTopBar(
        title = stringResource(Res.string.starred_repositories),
        subtitle = subtitle,
        size = KomiTopBarSize.Compact,
        leading = {
            KomiIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.navigate_back),
                onClick = { onAction(StarredReposAction.OnNavigateBackClick) },
                variant = KomiButtonVariant.Tonal,
            )
        },
        actions = {
            if (isSyncing) {
                KomiCircularProgress(modifier = Modifier.size(24.dp))
            }

            if (hasRepos) {
                KomiDropdown(
                    entries = sortEntries,
                    value = sortRule.name,
                    onSelect = { item ->
                        onAction(StarredReposAction.OnSortRuleSelected(StarredSortRule.valueOf(item.id)))
                    },
                    trigger = { onClick ->
                        KomiIconButton(
                            icon = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(Res.string.sort_label),
                            onClick = onClick,
                            variant = KomiButtonVariant.Tonal,
                        )
                    },
                )
            }
        },
    )
}

@Composable
private fun StarredSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    KomiTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        placeholder = stringResource(Res.string.search_repositories_hint),
        leadingIcon = Icons.Filled.Search,
        trailing = {
            if (query.isNotEmpty()) {
                KomiIcon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.clear_search),
                    modifier = Modifier.clickable { onQueryChange("") },
                )
            }
        },
    )
}

@Composable
private fun EmptyStateContent(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    val colors = LocalPersonality.current.colors
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KomiIcon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
        )

        Spacer(modifier = Modifier.height(16.dp))

        KomiText(
            text = title,
            role = KomiTextRole.Title,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            uppercase = false,
        )

        Spacer(modifier = Modifier.height(8.dp))

        KomiText(
            text = message,
            role = KomiTextRole.Body,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            uppercase = false,
        )

        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(16.dp))

            KomiButton(
                onClick = onActionClick,
                label = actionText,
                variant = KomiButtonVariant.Primary,
            )
        }
    }
}

/**
 * Header for the repositories the installability check withheld.
 *
 * Rendering a count with a disclosure control is the point of #1040: the previous behaviour
 * dropped these rows during sync, so "Last synced: just now" could sit above an incomplete
 * list with nothing on screen to suggest anything was missing.
 */
@Composable
private fun HiddenRepositoriesHeader(
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors

    KomiSurface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        elevation = KomiSurfaceElevation.Flat,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KomiText(
                text =
                    pluralStringResource(
                        Res.plurals.starred_hidden_repos_count,
                        count,
                        count,
                    ),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
                uppercase = false,
                modifier = Modifier.weight(1f),
            )
            KomiText(
                text =
                    stringResource(
                        if (isExpanded) {
                            Res.string.starred_hidden_repos_hide
                        } else {
                            Res.string.starred_hidden_repos_show
                        },
                    ),
                role = KomiTextRole.Label,
                color = colors.primary,
                uppercase = false,
            )
        }
    }
}

/**
 * One withheld repository and the reason it is withheld. Kept deliberately compact: the card
 * style would imply it is offerable, which is exactly the confusion this section removes.
 */
@Composable
private fun HiddenRepositoryRow(
    repository: StarredRepositoryUi,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors

    KomiSurface(
        modifier = modifier.fillMaxWidth(),
        elevation = KomiSurfaceElevation.Flat,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            KomiText(
                text = "${repository.repoOwner}/${repository.repoName}",
                role = KomiTextRole.Body,
                color = colors.onSurface,
                uppercase = false,
            )
            KomiText(
                text = exclusionReasonLabel(repository.exclusionReason),
                role = KomiTextRole.Label,
                color = colors.onSurfaceVariant,
                uppercase = false,
            )
        }
    }
}

@Composable
private fun exclusionReasonLabel(reason: StarredExclusionReason?): String =
    when (reason) {
        StarredExclusionReason.NO_RELEASES -> stringResource(Res.string.starred_hidden_reason_no_releases)
        StarredExclusionReason.PRERELEASE_ONLY ->
            stringResource(Res.string.starred_hidden_reason_prerelease_only)
        StarredExclusionReason.NO_MATCHING_ASSET ->
            stringResource(Res.string.starred_hidden_reason_no_matching_asset)
        StarredExclusionReason.PROBE_FAILED ->
            stringResource(Res.string.starred_hidden_reason_probe_failed)
        null -> stringResource(Res.string.starred_hidden_reason_probe_failed)
    }

@Preview
@Composable
private fun PreviewStarred() {
    PersonalityPreview {
        StarredReposScreen(
            state =
                StarredReposState(
                    starredRepositories = persistentListOf(),
                    isAuthenticated = true,
                ),
            onAction = {},
        )
    }
}

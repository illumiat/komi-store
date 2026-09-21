package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease

// Shared release-window normalization for the forge-backed sources (backend API
// and Forgejo): drop drafts, newest first, map to domain, gate pre-releases.
// The GitHub-direct fallback in InstalledAppsRepositoryImpl.fetchReleaseWindow
// still applies its own equivalent sort/filter — keep the two in sync.
fun List<ReleaseNetwork>.toReleaseWindow(includePreReleases: Boolean): List<GithubRelease> =
    asSequence()
        .filter { it.draft != true }
        .sortedByDescending { it.publishedAt ?: it.createdAt ?: "" }
        .map { it.toDomain() }
        .filter { includePreReleases || !it.isEffectivelyPreRelease() }
        .toList()

package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease

fun List<ReleaseNetwork>.toReleaseWindow(includePreReleases: Boolean): List<GithubRelease> =
    asSequence()
        .filter { it.draft != true }
        .sortedByDescending { it.publishedAt ?: it.createdAt ?: "" }
        .map { it.toDomain() }
        .filter { includePreReleases || !it.isEffectivelyPreRelease() }
        .toList()

// The window a check judges from when the list was fetched by someone else: the details
// refresh holds the repository host's own answer, and the check must not pay for a second
// read. Newest first, with the same pre-release filter and the same cap as the fetch path,
// so a longer list cannot widen the decision.
fun List<GithubRelease>.toUpdateCheckWindow(
    includePreReleases: Boolean,
    limit: Int,
): List<GithubRelease> =
    asSequence()
        .sortedByDescending { it.publishedAt }
        .filter { includePreReleases || !it.isEffectivelyPreRelease() }
        .take(limit)
        .toList()

package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.local.db.entities.StarredRepositoryEntity
import zed.rainxch.core.domain.model.repository.StarredExclusionReason
import zed.rainxch.core.domain.model.repository.StarredRepository

fun StarredRepository.toEntity(): StarredRepositoryEntity =
    StarredRepositoryEntity(
        repoId = repoId,
        repoName = repoName,
        repoOwner = repoOwner,
        repoOwnerAvatarUrl = repoOwnerAvatarUrl,
        repoDescription = repoDescription,
        primaryLanguage = primaryLanguage,
        repoUrl = repoUrl,
        stargazersCount = stargazersCount,
        forksCount = forksCount,
        openIssuesCount = openIssuesCount,
        isInstalled = isInstalled,
        installedPackageName = installedPackageName,
        latestVersion = latestVersion,
        latestReleaseUrl = latestReleaseUrl,
        starredAt = starredAt,
        addedAt = addedAt,
        lastSyncedAt = lastSyncedAt,
        exclusionReason = exclusionReason?.name,
    )

fun StarredRepositoryEntity.toDomain(): StarredRepository =
    StarredRepository(
        repoId = repoId,
        repoName = repoName,
        repoOwner = repoOwner,
        repoOwnerAvatarUrl = repoOwnerAvatarUrl,
        repoDescription = repoDescription,
        primaryLanguage = primaryLanguage,
        repoUrl = repoUrl,
        stargazersCount = stargazersCount,
        forksCount = forksCount,
        openIssuesCount = openIssuesCount,
        isInstalled = isInstalled,
        installedPackageName = installedPackageName,
        latestVersion = latestVersion,
        latestReleaseUrl = latestReleaseUrl,
        starredAt = starredAt,
        addedAt = addedAt,
        lastSyncedAt = lastSyncedAt,
        // A reason this build does not know about still means "not shown": keeping the row
        // hidden is the safe reading, and it stops a downgrade from promoting an excluded
        // repository back into the installable list.
        exclusionReason = exclusionReason?.toExclusionReason(),
    )

private fun String.toExclusionReason(): StarredExclusionReason =
    StarredExclusionReason.entries.firstOrNull { it.name == this }
        ?: StarredExclusionReason.PROBE_FAILED

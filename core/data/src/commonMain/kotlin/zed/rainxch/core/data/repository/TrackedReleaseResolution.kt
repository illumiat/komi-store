package zed.rainxch.core.data.repository

import co.touchlab.kermit.Logger
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.utils.AssetFilter
import zed.rainxch.core.domain.utils.AssetVariant
import zed.rainxch.core.domain.utils.ResolvedRelease
import zed.rainxch.core.domain.utils.TrackedReleaseResolver

internal suspend fun resolveTrackedReleaseFor(
    installedAppsDao: InstalledAppDao,
    installer: Installer,
    app: InstalledAppEntity,
    releases: List<GithubRelease>,
): ResolvedRelease? {
    val compiledFilter =
        AssetFilter
            .parse(app.assetFilterRegex)
            ?.onFailure { error ->
                Logger.w {
                    "Invalid asset filter for ${app.packageName} " +
                        "(${app.assetFilterRegex}): ${error.message} — ignoring"
                }
            }?.getOrNull()

    return TrackedReleaseResolver.resolve(
        releases = releases,
        filter = compiledFilter,
        fallbackToOlderReleases = app.fallbackToOlderReleases,
        preferredVariant = app.preferredAssetVariant,
        preferredTokens = AssetVariant.deserializeTokens(app.preferredAssetTokens),
        preferredGlob = app.assetGlobPattern,
        pickedIndex = app.pickedAssetIndex,
        pickedSiblingCount = app.pickedAssetSiblingCount,
        trackedPackageName = app.packageName,
        installedAssetName = app.installedAssetName,
        repoApps = installedAppsDao.getAppsByRepoId(app.repoId).map { it.toDomain() },
        installer = installer,
    )
}

package zed.rainxch.core.data.local.db.dao

import zed.rainxch.core.data.local.db.entities.InstalledAppEntity

// Small DAO write helpers that the repository uses, kept here so the repository file stays
// within the architecture line limit.
internal suspend fun InstalledAppDao.recordUnmatchedRelease(packageName: String) {
    clearUpdateFlagKeepBaseline(packageName, System.currentTimeMillis())
}

internal suspend fun InstalledAppDao.adoptMatchedTag(
    app: InstalledAppEntity,
    matchedTag: String,
    isUpdateAvailable: Boolean,
) {
    updateInstalledVersion(
        packageName = app.packageName,
        installedVersion = matchedTag,
        installedVersionName = app.installedVersionName,
        installedVersionCode = app.installedVersionCode,
        isUpdateAvailable = isUpdateAvailable,
    )
}

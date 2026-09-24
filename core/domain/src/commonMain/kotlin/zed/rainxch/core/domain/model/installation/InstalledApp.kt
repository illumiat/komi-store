package zed.rainxch.core.domain.model.installation
data class InstalledApp(
    val packageName: String,
    val repoId: Long,
    val repoName: String,
    val repoOwner: String,
    val repoOwnerAvatarUrl: String,
    val repoDescription: String?,
    val primaryLanguage: String?,
    val repoUrl: String,
    val installedVersion: String,
    val installedAssetName: String?,
    val installedAssetUrl: String?,
    val latestVersion: String?,
    val latestAssetName: String?,
    val latestAssetUrl: String?,
    val latestAssetSize: Long?,
    /**
     * Identity of the release and asset the latest snapshot was taken from. For a tag
     * that names many builds (`nightly`) this — not `latestVersion` — is what tells one
     * build from the next, and it is what a check compares to decide whether the release
     * the tag points at has been replaced. Null on rows written before identities were
     * stored, which the check reads as "not recorded" and falls back to the publish time.
     */
    val latestReleaseId: Long? = null,
    val latestAssetId: Long? = null,
    /**
     * Digest of the asset the latest snapshot was taken from — the content fallback for
     * a host that supplies no asset id. Null when the host supplied none.
     */
    val latestAssetDigest: String? = null,
    val appName: String,
    val installSource: InstallSource,
    val installedAt: Long,
    val lastCheckedAt: Long,
    val lastUpdatedAt: Long,
    val isUpdateAvailable: Boolean,
    val signingFingerprint: String?,
    val updateCheckEnabled: Boolean = true,
    val releaseNotes: String? = "",
    val systemArchitecture: String,
    val fileExtension: String,
    val isPendingInstall: Boolean = false,
    val installedVersionName: String? = null,
    val installedVersionCode: Long = 0L,
    val latestVersionName: String? = null,
    val latestVersionCode: Long? = null,
    val latestReleasePublishedAt: String? = null,
    val includePreReleases: Boolean = false,

    val assetFilterRegex: String? = null,

    val fallbackToOlderReleases: Boolean = false,

    val preferredAssetVariant: String? = null,

    val preferredVariantStale: Boolean = false,

    val preferredAssetTokens: String? = null,

    val assetGlobPattern: String? = null,

    val pickedAssetIndex: Int? = null,

    val pickedAssetSiblingCount: Int? = null,

    val pendingInstallFilePath: String? = null,

    val pendingInstallVersion: String? = null,

    val pendingInstallAssetName: String? = null,

    val skippedReleaseTag: String? = null,
    val sourceHost: String? = null,
)

fun InstalledApp?.isReallyInstalled(): Boolean = this != null && !this.isPendingInstall

fun InstalledApp?.hasActualUpdate(): Boolean =
    this != null && this.isUpdateAvailable && !this.isPendingInstall

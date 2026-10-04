package zed.rainxch.core.domain.system

data class OrchestratedDownload(
    val id: String,
    val packageName: String,
    val repoOwner: String,
    val repoName: String,
    val displayAppName: String,
    val assetName: String,
    val assetSize: Long,

    /**
     * GitHub asset identity, retained so [DownloadOrchestrator.resume] can rebuild a faithful
     * [DownloadSpec] from a paused entry instead of degrading ownership/verification to size-only.
     * Defaulted to keep every existing construction site source-compatible; the orchestrator writes
     * the real values from [DownloadSpec.asset] when the entry is created.
     */
    val assetId: Long = 0L,
    val assetDigest: String? = null,

    val downloadUrl: String,
    val releaseTag: String,

    val filePath: String?,

    val installPolicy: InstallPolicy,
    val stage: DownloadStage,

    val progressPercent: Int?,

    val bytesDownloaded: Long = 0L,

    val totalBytes: Long? = null,

    val errorMessage: String? = null,
    val installOutcome: InstallOutcome? = null,
)

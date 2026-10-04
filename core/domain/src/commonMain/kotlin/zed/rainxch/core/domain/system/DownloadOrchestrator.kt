package zed.rainxch.core.domain.system

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DownloadOrchestrator {

    val downloads: StateFlow<Map<String, OrchestratedDownload>>

    fun observe(packageName: String): Flow<OrchestratedDownload?>

    suspend fun enqueue(spec: DownloadSpec): String

    suspend fun downgradeToDeferred(packageName: String)

    /**
     * D-8 "pause": stop the transfer but keep everything needed to continue — the entry moves to
     * [DownloadStage.Paused] with its progress intact, and the partial + sidecar stay on disk.
     *
     * This is deliberately *not* a delete. Only [discard] removes the entry and the bytes; a paused
     * download therefore stays visible so [resume] can pick it up where it left off.
     */
    suspend fun cancel(packageName: String)

    /**
     * Resume a download that [cancel] paused: re-issues the transfer for the paused entry, which
     * the downloader continues from the retained partial via a `Range` request.
     *
     * A no-op when [packageName] has no paused entry — resuming something that is not paused must
     * never restart it.
     */
    suspend fun resume(packageName: String)

    /**
     * D-8 "delete": stop the download *and* clean up — the partial, its sidecar and the list entry.
     *
     * This is the counterpart of [cancel], which is "pause" and deliberately keeps the partial so a
     * later [resume] can continue from it. The distinction is the user's explicit intent, never the
     * exception type: a pause, a process kill and a read timeout all look alike to the coroutine machinery.
     */
    suspend fun discard(packageName: String)

    suspend fun installPending(packageName: String): InstallOutcome?

    fun dismiss(packageName: String)
}

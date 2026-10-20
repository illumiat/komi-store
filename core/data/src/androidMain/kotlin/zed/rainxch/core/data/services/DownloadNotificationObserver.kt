package zed.rainxch.core.data.services

import android.os.SystemClock
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import zed.rainxch.core.data.download.DownloadNotificationAction
import zed.rainxch.core.data.download.ForegroundPrimary
import zed.rainxch.core.data.download.notificationActionsFor
import zed.rainxch.core.domain.system.DownloadOrchestrator
import zed.rainxch.core.domain.system.DownloadProgressNotifier
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.OrchestratedDownload

class DownloadNotificationObserver(
    private val orchestrator: DownloadOrchestrator,
    private val notifier: DownloadProgressNotifier,
    private val foreground: DownloadForegroundController,
) {
    @Volatile
    private var job: Job? = null

    private val lastStages = mutableMapOf<String, DownloadStage>()
    private val lastNotifiedAt = mutableMapOf<String, Long>()

    private val activeSince = mutableMapOf<String, Long>()

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job =
            scope.launch {
                try {
                    orchestrator.downloads.collect { snapshot ->
                        try {
                            reconcile(snapshot)
                        } catch (t: Throwable) {
                            Logger.w(t) { "DownloadNotificationObserver: reconcile failed, continuing" }
                        }
                    }
                } finally {
                    job = null
                }
            }
    }

    private fun reconcile(snapshot: Map<String, OrchestratedDownload>) {
        val removed = lastStages.keys - snapshot.keys
        for (pkg in removed) {
            clearProgressSafely(pkg)
            lastStages.remove(pkg)
            lastNotifiedAt.remove(pkg)
            activeSince.remove(pkg)
        }

        for ((pkg, entry) in snapshot) {
            reconcileEntry(pkg, entry, lastStages[pkg])
            lastStages[pkg] = entry.stage
        }

        reconcileForeground(snapshot)
    }

    private fun reconcileEntry(
        pkg: String,
        entry: OrchestratedDownload,
        previous: DownloadStage?,
    ) {
        val actions = notificationActionsFor(entry.stage)
        when {
            DownloadNotificationAction.PAUSE in actions -> trackProgress(pkg, entry, previous)
            DownloadNotificationAction.RESUME in actions -> trackPaused(pkg, entry, previous)
            else -> dropIfNotified(pkg, previous)
        }
    }

    private fun trackProgress(
        pkg: String,
        entry: OrchestratedDownload,
        previous: DownloadStage?,
    ) {
        if (previous != DownloadStage.Queued && previous != DownloadStage.Downloading) {
            activeSince[pkg] = SystemClock.uptimeMillis()
        }
        val now = SystemClock.uptimeMillis()
        val last = lastNotifiedAt[pkg] ?: 0L
        val due =
            previous != entry.stage ||
                entry.progressPercent == 100 ||
                (now - last) >= PROGRESS_UPDATE_INTERVAL_MS
        if (!due) return

        postProgress(pkg, entry, paused = false)
        lastNotifiedAt[pkg] = now
    }

    private fun trackPaused(
        pkg: String,
        entry: OrchestratedDownload,
        previous: DownloadStage?,
    ) {
        if (previous != entry.stage) {
            postProgress(pkg, entry, paused = true)
            lastNotifiedAt[pkg] = SystemClock.uptimeMillis()
        }
        activeSince.remove(pkg)
    }

    private fun dropIfNotified(
        pkg: String,
        previous: DownloadStage?,
    ) {
        if (previous != null && notificationActionsFor(previous).isNotEmpty()) {
            clearProgressSafely(pkg)
            lastNotifiedAt.remove(pkg)
        }
        activeSince.remove(pkg)
    }

    private fun reconcileForeground(snapshot: Map<String, OrchestratedDownload>) {
        val active =
            snapshot.filterValues {
                it.stage == DownloadStage.Queued || it.stage == DownloadStage.Downloading
            }
        if (active.isEmpty()) {
            foreground.stop()
            return
        }
        val primaryPackage = ForegroundPrimary.choose(active.keys, activeSince)
        foreground.ensureRunning(primary = primaryPackage?.let { active[it] })
    }

    private fun postProgress(
        pkg: String,
        entry: OrchestratedDownload,
        paused: Boolean,
    ) {
        try {
            notifier.notifyProgress(
                packageName = pkg,
                appName = entry.displayAppName,
                versionTag = entry.releaseTag.ifBlank { entry.assetName },
                percent = entry.progressPercent,
                bytesDownloaded = entry.bytesDownloaded,
                totalBytes = entry.totalBytes,
                paused = paused,
            )
        } catch (t: Throwable) {
            Logger.w(t) { "DownloadNotificationObserver: notifyProgress failed for $pkg" }
        }
    }

    private fun clearProgressSafely(pkg: String) {
        try {
            notifier.clearProgress(pkg)
        } catch (t: Throwable) {
            Logger.w(t) { "DownloadNotificationObserver: clearProgress failed for $pkg" }
        }
    }

    private companion object {
        const val PROGRESS_UPDATE_INTERVAL_MS = 400L
    }
}

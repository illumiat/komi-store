package zed.rainxch.core.data.services

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import zed.rainxch.core.domain.system.OrchestratedDownload

class DownloadForegroundController(
    private val context: Context,
) {
    @Volatile
    private var running = false

    @Volatile
    private var degraded = false

    @Volatile
    private var foregroundPackage: String? = null

    fun ensureRunning(primary: OrchestratedDownload?) {
        val target = startable(primary) ?: return
        if (running && target.packageName == foregroundPackage) return

        start(target)
    }

    private fun startable(primary: OrchestratedDownload?): OrchestratedDownload? =
        primary?.takeIf { !degraded && DownloadNotificationFactory.hasNotificationPermission(context) }

    private fun start(primary: OrchestratedDownload) {
        val intent =
            DownloadForegroundService.startIntent(
                context = context,
                content =
                    DownloadForegroundService.Content(
                        notificationId = DownloadNotificationFactory.notificationIdFor(primary.packageName),
                        title = primary.displayAppName.ifBlank { primary.packageName },
                        text =
                            DownloadNotificationFactory.formatProgressText(
                                versionTag = primary.releaseTag.ifBlank { primary.assetName },
                                bytesDownloaded = primary.bytesDownloaded,
                                totalBytes = primary.totalBytes,
                            ),
                        percent = primary.progressPercent,
                        cancelPackageName = primary.packageName,
                    ),
            )
        val result = runCatching { ContextCompat.startForegroundService(context, intent) }
        if (result.isFailure) {
            Logger.w(result.exceptionOrNull()) {
                "DownloadForegroundController: FGS start refused for ${primary.packageName}, " +
                    "degrading to plain notification"
            }
            degrade()
            return
        }
        running = true
        foregroundPackage = primary.packageName
    }

    // An already-running service holds the previous primary's notification, which the observer
    // cancels when that download ends, leaving a foreground service with no notification. Tear it
    // down and keep the plain notification path.
    private fun degrade() {
        if (running) stop()
        degraded = true
    }

    fun stop() {
        val serviceWasAlive = running
        val lastForegroundPackage = foregroundPackage
        running = false
        degraded = false
        foregroundPackage = null
        if (!serviceWasAlive && lastForegroundPackage == null) return

        runCatching {
            context.stopService(Intent(context, DownloadForegroundService::class.java))
        }
        if (!serviceWasAlive && lastForegroundPackage != null) {
            runCatching {
                NotificationManagerCompat
                    .from(context)
                    .cancel(DownloadNotificationFactory.notificationIdFor(lastForegroundPackage))
            }
        }
    }
}

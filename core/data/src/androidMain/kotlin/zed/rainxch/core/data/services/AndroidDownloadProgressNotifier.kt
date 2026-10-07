package zed.rainxch.core.data.services

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import zed.rainxch.core.domain.system.DownloadProgressNotifier

class AndroidDownloadProgressNotifier(
    private val context: Context,
    private val pauseLabel: String = "Pause",
    private val resumeLabel: String = "Resume",
    private val deleteLabel: String = "Delete",
) : DownloadProgressNotifier {
    @SuppressLint("MissingPermission")
    override fun notifyProgress(
        packageName: String,
        appName: String,
        versionTag: String,
        percent: Int?,
        bytesDownloaded: Long,
        totalBytes: Long?,
        paused: Boolean,
    ) {
        if (!DownloadNotificationFactory.hasNotificationPermission(context)) return

        val notification =
            DownloadNotificationFactory.buildNotification(
                context = context,
                title = appName,
                text =
                    DownloadNotificationFactory.formatProgressText(
                        versionTag = versionTag,
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                    ),
                percent = percent,
                cancelPackageName = packageName,
                paused = paused,
                pauseLabel = pauseLabel,
                resumeLabel = resumeLabel,
                deleteLabel = deleteLabel,
            )
        NotificationManagerCompat
            .from(context)
            .notify(DownloadNotificationFactory.notificationIdFor(packageName), notification)
    }

    override fun clearProgress(packageName: String) {
        NotificationManagerCompat
            .from(context)
            .cancel(DownloadNotificationFactory.notificationIdFor(packageName))
    }
}

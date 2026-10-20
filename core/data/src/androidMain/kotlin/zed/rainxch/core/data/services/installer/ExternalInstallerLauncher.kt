package zed.rainxch.core.data.services.installer

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import co.touchlab.kermit.Logger
import java.io.File

class ExternalInstallerLauncher(
    private val context: Context,
) {
    fun launch(
        filePath: String,
        packageName: String?,
    ): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val targeted = !packageName.isNullOrBlank()
        val candidates =
            listOf(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME),
                Intent(ACTION_INSTALL_PACKAGE).setDataAndType(uri, APK_MIME),
            )
        val started = candidates.any { base -> startTargeted(base, packageName, targeted) }
        if (!started) {
            Logger.d(TAG) { "Explicit installer launch failed, falling back to the system chooser" }
        }
        return started || startChooser(uri)
    }

    private fun startTargeted(
        base: Intent,
        packageName: String?,
        targeted: Boolean,
    ): Boolean {
        val intent =
            Intent(base).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (targeted) setPackage(packageName)
            }
        val resolvable = targeted || intent.resolveActivity(context.packageManager) != null
        return resolvable && runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun startChooser(uri: android.net.Uri): Boolean =
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, APK_MIME)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.isSuccess

    private companion object {
        const val TAG = "ExternalInstaller"
        const val APK_MIME = "application/vnd.android.package-archive"
        const val ACTION_INSTALL_PACKAGE = "android.intent.action.INSTALL_PACKAGE"
    }
}

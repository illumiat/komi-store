package zed.rainxch.githubstore.app.diagnostics

import zed.rainxch.core.domain.diagnostics.DiagnosticSessionController
import zed.rainxch.core.domain.system.AppVersionInfo

internal class AndroidDiagnosticSessionController(
    private val appVersionInfo: AppVersionInfo,
) : DiagnosticSessionController {
    override val isEnabled: Boolean
        get() = DiagnosticSession.enabled

    override fun setEnabled(enabled: Boolean) {
        DiagnosticSession.setEnabled(enabled)
    }

    override fun hasLogsToShare(): Boolean = DiagnosticSession.hasLogsToShare()

    override suspend fun buildShareBundle(): String? = DiagnosticSession.buildShareBundle(appVersionInfo.versionName)
}

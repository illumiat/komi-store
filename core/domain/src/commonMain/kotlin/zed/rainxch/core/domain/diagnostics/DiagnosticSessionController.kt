package zed.rainxch.core.domain.diagnostics

interface DiagnosticSessionController {
    val isEnabled: Boolean

    fun setEnabled(enabled: Boolean)

    fun hasLogsToShare(): Boolean

    suspend fun buildShareBundle(): String?
}

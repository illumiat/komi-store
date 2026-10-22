package zed.rainxch.core.domain.diagnostics

interface DiagnosticSessionController {
    val isEnabled: Boolean

    fun setEnabled(enabled: Boolean)

    fun hasSessionLog(): Boolean

    suspend fun buildShareBundle(): String?
}

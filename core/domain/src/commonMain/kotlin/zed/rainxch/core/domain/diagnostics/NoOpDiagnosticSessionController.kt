package zed.rainxch.core.domain.diagnostics

object NoOpDiagnosticSessionController : DiagnosticSessionController {
    override val isEnabled: Boolean = false

    override fun setEnabled(enabled: Boolean) = Unit

    override fun hasSessionLog(): Boolean = false

    override suspend fun buildShareBundle(): String? = null
}

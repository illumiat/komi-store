package zed.rainxch.core.domain.model.installation

data class SystemPackageInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isInstalled: Boolean,
    val signingFingerprint: String?,
) {
    companion object {
        const val UNKNOWN_VERSION_NAME: String = "unknown"
    }
}

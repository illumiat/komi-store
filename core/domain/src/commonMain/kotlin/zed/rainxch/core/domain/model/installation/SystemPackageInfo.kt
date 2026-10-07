package zed.rainxch.core.domain.model.installation

data class SystemPackageInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isInstalled: Boolean,
    val signingFingerprint: String?,
) {
    companion object {
        // What core/data's monitors write where Android's PackageInfo.versionName is null. Shared
        // so the landing proof can treat it as "no name reported" instead of a real build name
        // that arrived.
        const val UNKNOWN_VERSION_NAME: String = "unknown"
    }
}

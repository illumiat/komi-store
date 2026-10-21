package zed.rainxch.core.domain.model.installation

data class SystemPackageInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isInstalled: Boolean,
    val signingFingerprint: String?,
    // Null on desktop, which has no installed APK to read.
    val apkPath: String? = null,
    // Byte size of the installed base APK; null when the platform cannot tell.
    // Comparing it with the release asset size proves a different build without hashing.
    val apkSize: Long? = null,
    // Whether the current install came from this app. False means another installer (browser,
    // file manager, adb) replaced it, so a stored binding cannot be trusted without a hash.
    // Null when the platform cannot tell.
    val installedBySelf: Boolean? = null,
    // PackageManager's install timestamp: it moves on every install, even when the version
    // fields stay the same, so a park older than this proves the install ran.
    val lastUpdateTime: Long? = null,
) {
    companion object {
        const val UNKNOWN_VERSION_NAME: String = "unknown"
    }
}

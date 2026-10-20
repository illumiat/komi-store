package zed.rainxch.core.data.repository

internal enum class ParkedFileDisposal {
    Gone,

    Survived,
}

internal fun tryDeleteParkedFile(path: String): ParkedFileDisposal {
    val file = java.io.File(path)
    val deleted = runCatching { file.delete() }.getOrDefault(false)
    if (deleted) return ParkedFileDisposal.Gone
    return if (file.exists()) ParkedFileDisposal.Survived else ParkedFileDisposal.Gone
}

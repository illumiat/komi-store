package zed.rainxch.core.data.download

// The source answered 404/410: the asset is not at the URL the download was handed any more.
// Kept apart from other failures on purpose — this is the shape a stale resolution leaves
// behind, and the one shape worth re-resolving the release for before giving up.
class AssetSourceGoneException(
    val statusCode: Int,
) : Exception("Unexpected code $statusCode") {
    companion object {
        private const val STATUS_NOT_FOUND = 404
        private const val STATUS_GONE = 410

        fun isGoneCode(statusCode: Int): Boolean = statusCode == STATUS_NOT_FOUND || statusCode == STATUS_GONE
    }
}

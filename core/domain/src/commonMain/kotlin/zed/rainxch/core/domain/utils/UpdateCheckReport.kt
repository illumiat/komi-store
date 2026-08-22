package zed.rainxch.core.domain.utils

import kotlinx.datetime.TimeZone

data class UpdateCheckReport(
    val usedTimestampLogic: Boolean,
    val branch: String,
    val reason: String,
    val windowSource: String,
    val windowSize: Int,
    val includePreReleases: Boolean,
    val opaqueMatched: Boolean,
    val sameTag: Boolean,
    val reconcilable: Boolean,
    val installedTag: String,
    val matchedTag: String?,
    val matchedAssetName: String? = null,
    val matchedAssetUrl: String? = null,
    val storedPublishedAt: String?,
    val matchedPublishedAt: String?,
    val codesAlreadyMatch: Boolean,
    val isUpdate: Boolean,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    fun format(): String {
        val storedLocal = DeviceLocalTime.toDeviceLocal(storedPublishedAt, timeZone)
        val matchedLocal = DeviceLocalTime.toDeviceLocal(matchedPublishedAt, timeZone)
        return listOf(
            "usedTimestampLogic=$usedTimestampLogic",
            "branch=$branch",
            "reason=$reason",
            "windowSource=$windowSource",
            "windowSize=$windowSize",
            "includePreReleases=$includePreReleases",
            "opaqueMatched=$opaqueMatched",
            "sameTag=$sameTag",
            "reconcilable=$reconcilable",
            "installedTag=$installedTag",
            "matchedTag=${matchedTag ?: "null"}",
            "matchedAssetName=${matchedAssetName ?: "null"}",
            "matchedAssetUrl=${matchedAssetUrl ?: "null"}",
            "storedPublishedAt=${storedPublishedAt ?: "null"}",
            "storedPublishedAtLocal=${storedLocal?.localDateTime ?: "null"}",
            "matchedPublishedAt=${matchedPublishedAt ?: "null"}",
            "matchedPublishedAtLocal=${matchedLocal?.localDateTime ?: "null"}",
            "deviceTimeZone=${timeZone.id}",
            "codesAlreadyMatch=$codesAlreadyMatch",
            "isUpdate=$isUpdate",
        ).joinToString("\n")
    }
}

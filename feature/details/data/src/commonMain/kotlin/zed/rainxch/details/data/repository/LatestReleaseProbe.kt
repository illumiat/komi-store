package zed.rainxch.details.data.repository

import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import zed.rainxch.core.data.dto.ReleaseNetwork

// GET /repos/{owner}/{repo}/releases/latest answers with one release instead of a page of ten.
// It answers 404 when the repository has no published release, and a proxy, a mirror or a future
// API can answer with something this client cannot use. Both cases must read the release list
// instead of reporting the repository as having no release at all.

internal sealed interface LatestReleaseProbe {
    data class Published(
        val release: ReleaseNetwork,
    ) : LatestReleaseProbe

    data object NotPublished : LatestReleaseProbe

    data object Unsupported : LatestReleaseProbe
}

// The fallback decision: only a usable answer is trusted. A 404, an empty body, a body this client
// cannot decode, a draft/pre-release answer that breaks the endpoint contract, or any other
// non-success status sends the read through the release list.
internal fun classifyLatestReleaseProbe(
    status: HttpStatusCode,
    release: ReleaseNetwork?,
): LatestReleaseProbe =
    when {
        status == HttpStatusCode.NotFound -> LatestReleaseProbe.NotPublished
        !status.isSuccess() -> LatestReleaseProbe.Unsupported
        release == null -> LatestReleaseProbe.Unsupported
        release.draft == true || release.prerelease == true -> LatestReleaseProbe.Unsupported
        else -> LatestReleaseProbe.Published(release)
    }

// The rule the fallback path always applied: the newest release that is neither a draft nor a
// pre-release, ordered by publishedAt and by createdAt for releases published before that field
// existed.
internal fun selectLatestPublishedRelease(releases: List<ReleaseNetwork>): ReleaseNetwork? =
    releases
        .asSequence()
        .filter { it.draft != true && it.prerelease != true }
        .maxByOrNull { it.publishedAt ?: it.createdAt ?: "" }

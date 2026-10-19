package zed.rainxch.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubStarredResponse(
    val id: Long,
    val name: String,
    val owner: Owner,
    val description: String?,
    val language: String?,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("stargazers_count") val stargazersCount: Int,
    @SerialName("forks_count") val forksCount: Int,
    @SerialName("open_issues_count") val openIssuesCount: Int,
) {
    @Serializable
    data class Owner(
        val login: String,
        @SerialName("avatar_url") val avatarUrl: String,
    )
}

/**
 * One entry of `GET /user/starred` when the request asks for
 * `application/vnd.github.star+json`.
 *
 * Without that media type the endpoint returns bare repository objects and never sends
 * `starred_at`, so the star time was always null. The "Recently starred" sort then fell
 * through to its name tie-breaker and sorted alphabetically (#822).
 *
 * `starred_at` stays nullable because GitHub is free to omit it (an entry starred through
 * an integration has been seen without one); the sort already treats a missing value as
 * oldest rather than letting it jump to the top.
 */
@Serializable
data class GitHubStarredItem(
    @SerialName("starred_at") val starredAt: String? = null,
    val repo: GitHubStarredResponse,
)

package zed.rainxch.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Serializable
data class GitHubStarredResponseWithTimestamp(
    @SerialName("starred_at") val starredAt: String? = null,
    val repo: GitHubStarredResponse,
)

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
    @SerialName("starred_at") val starredAt: String? = null,
) {
    @Serializable
    data class Owner(
        val login: String,
        @SerialName("avatar_url") val avatarUrl: String,
    )
}

/**
 * Reads one page of a starred listing, tolerating both wire shapes.
 *
 * With `application/vnd.github.star+json` each entry is `{starred_at, repo}`. Without it - a
 * host or proxy that ignores the media type, or a request that ends up carrying two Accept
 * values - each entry is the bare repository object and there is no star time anywhere.
 *
 * Decoding straight into the wrapper makes the bare shape a `MissingFieldException`, which
 * fails the whole sync rather than costing one ordering hint. Parsing by hand keeps the
 * repository and drops only the timestamp. It is done this way rather than with a
 * `JsonTransformingSerializer` on the wrapper because annotating the class with one makes its
 * generated `serializer()` resolve to that serializer, whose constructor asks for it again:
 * an initialiser cycle that surfaces as `ExceptionInInitializerError`.
 */
internal fun parseStarredPage(
    json: Json,
    element: JsonElement,
): List<GitHubStarredResponseWithTimestamp> =
    element.jsonArray.map { entry ->
        val obj = entry.jsonObject
        if (REPO_KEY in obj) {
            json.decodeFromJsonElement(GitHubStarredResponseWithTimestamp.serializer(), obj)
        } else {
            GitHubStarredResponseWithTimestamp(
                starredAt = null,
                repo = json.decodeFromJsonElement(GitHubStarredResponse.serializer(), obj),
            )
        }
    }

private const val REPO_KEY = "repo"

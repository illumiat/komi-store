package zed.rainxch.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

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
 * One entry of a starred listing, in the shape the `star+json` media type produces.
 *
 * `starred_at` is nullable because GitHub omits it for entries starred through an
 * integration; the sort already treats a missing value as oldest rather than letting it
 * jump to the top.
 */
@Serializable
data class GitHubStarredItem(
    @SerialName("starred_at") val starredAt: String? = null,
    val repo: GitHubStarredResponse,
)

/**
 * Reads one page of a starred listing, tolerating both wire shapes.
 *
 * With `application/vnd.github.star+json` each entry is `{starred_at, repo}`. Without it -
 * a host or proxy that ignores the media type - each entry is the bare repository object and
 * there is no star time anywhere. Parsing is done by hand rather than with a custom
 * `JsonTransformingSerializer` because annotating [GitHubStarredItem] with one makes its
 * generated `serializer()` resolve to that serializer, whose own constructor then asks for it
 * again: an initialiser cycle that surfaces as `ExceptionInInitializerError`.
 *
 * A bare entry still yields a usable item; it simply has no `starredAt`, which the caller
 * reports rather than treating as fatal.
 */
internal fun parseStarredPage(
    json: Json,
    element: JsonElement,
): List<GitHubStarredItem> =
    element.jsonArray.map { entry ->
        val obj = entry.jsonObject
        if (REPO_KEY in obj) {
            json.decodeFromJsonElement(GitHubStarredItem.serializer(), obj)
        } else {
            GitHubStarredItem(
                starredAt = null,
                repo = json.decodeFromJsonElement(GitHubStarredResponse.serializer(), obj),
            )
        }
    }

private const val REPO_KEY = "repo"

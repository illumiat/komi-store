package zed.rainxch.core.data.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * `GET /user/starred` answers in two different shapes depending on the requested media type.
 * These tests pin both, so a future refactor cannot quietly go back to the bare shape and
 * lose the star time again (#822), and a host that ignores the media type cannot break the
 * sync by answering with the bare shape instead of the wrapper.
 */
class GitHubStarredItemTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    // The inner "starred_at" is deliberate: when GitHub sends the wrapper it also leaves a
    // stale field on the repository object, and picking that up instead of the wrapper's
    // value would be invisible in a passing test that only checked for null.
    private val repoJson =
        """
        {
          "id": 1296269,
          "name": "Hello-World",
          "owner": { "login": "octocat", "avatar_url": "https://avatars.githubusercontent.com/u/1" },
          "description": "My first repository",
          "language": "Kotlin",
          "html_url": "https://github.com/octocat/Hello-World",
          "stargazers_count": 80,
          "forks_count": 9,
          "open_issues_count": 3,
          "starred_at": "1999-01-01T00:00:00Z"
        }
        """.trimIndent()

    @Test
    fun `parses the star plus json entry shape`() {
        val payload =
            """
            [
              {
                "starred_at": "2026-10-01T12:34:56Z",
                "repo": $repoJson
              }
            ]
            """.trimIndent()

        val items = parseStarredPage(json, json.parseToJsonElement(payload))

        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("2026-10-01T12:34:56Z", item.starredAt)
        assertEquals(1296269L, item.repo.id)
        assertEquals("octocat", item.repo.owner.login)
    }

    @Test
    fun `an entry without a star time keeps the repository and stays unsorted-last`() {
        val payload = """[{ "repo": $repoJson }]""".trimIndent()

        val item = parseStarredPage(json, json.parseToJsonElement(payload)).single()

        // The repository is still usable; only the ordering hint is missing.
        assertNull(item.starredAt)
        assertNotNull(item.repo)
        assertEquals("Hello-World", item.repo.name)
    }

    @Test
    fun `a bare repository entry is normalised instead of failing the whole page`() {
        // What a host returns when it ignores the star+json media type. Decoding must not
        // throw: one unexpected shape would otherwise fail the entire sync and write nothing.
        val payload = """[$repoJson]""".trimIndent()

        val item = parseStarredPage(json, json.parseToJsonElement(payload)).single()

        assertNull(item.starredAt)
        assertEquals("Hello-World", item.repo.name)
        assertEquals(1296269L, item.repo.id)
    }
}

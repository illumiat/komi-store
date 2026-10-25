package zed.rainxch.details.data.repository

import io.ktor.http.HttpStatusCode
import zed.rainxch.core.data.dto.ReleaseNetwork
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LatestReleaseProbeTest {
    @Test
    fun a_published_release_from_the_short_endpoint_is_used_as_is() {
        val release = release(id = 7, tag = "v2.0.0")

        val probe = classifyLatestReleaseProbe(HttpStatusCode.OK, release)

        assertEquals(LatestReleaseProbe.Published(release), probe)
    }

    @Test
    fun a_404_for_the_short_endpoint_falls_back_to_the_release_list() {
        assertEquals(
            LatestReleaseProbe.NotPublished,
            classifyLatestReleaseProbe(HttpStatusCode.NotFound, release = null),
        )
    }

    @Test
    fun an_empty_or_undecodable_body_falls_back_to_the_release_list() {
        assertEquals(
            LatestReleaseProbe.Unsupported,
            classifyLatestReleaseProbe(HttpStatusCode.OK, release = null),
        )
    }

    @Test
    fun a_draft_or_prerelease_answer_falls_back_instead_of_being_trusted() {
        // /releases/latest is documented to exclude both; a proxy that breaks that promise must
        // not be trusted over the list filter.
        assertEquals(
            LatestReleaseProbe.Unsupported,
            classifyLatestReleaseProbe(HttpStatusCode.OK, release(id = 2, tag = "v2.0.0-beta.1", isPrerelease = true)),
        )
        assertEquals(
            LatestReleaseProbe.Unsupported,
            classifyLatestReleaseProbe(HttpStatusCode.OK, release(id = 3, tag = "v2.0.0", isDraft = true)),
        )
    }

    @Test
    fun any_other_non_success_answer_falls_back_to_the_release_list() {
        assertEquals(
            LatestReleaseProbe.Unsupported,
            classifyLatestReleaseProbe(HttpStatusCode.InternalServerError, release = null),
        )
    }

    @Test
    fun the_fallback_list_selects_the_newest_published_release() {
        val newestPrerelease = release(id = 4, tag = "v4.0.0-rc.1", publishedAt = "2026-04-01T00:00:00Z", isPrerelease = true)
        val newestDraft = release(id = 5, tag = "v5.0.0", publishedAt = "2026-05-01T00:00:00Z", isDraft = true)
        val stable = release(id = 3, tag = "v3.0.0", publishedAt = "2026-03-01T00:00:00Z")
        val olderStable = release(id = 2, tag = "v2.0.0", publishedAt = "2026-02-01T00:00:00Z")

        val selected = selectLatestPublishedRelease(listOf(newestPrerelease, newestDraft, olderStable, stable))

        assertEquals(stable, selected)
    }

    @Test
    fun the_fallback_list_uses_created_at_when_published_at_is_missing() {
        val createdAtOnly = release(id = 2, tag = "v2.0.0", publishedAt = null, createdAt = "2026-05-01T00:00:00Z")
        val published = release(id = 1, tag = "v1.0.0", publishedAt = "2026-04-01T00:00:00Z")

        val selected = selectLatestPublishedRelease(listOf(published, createdAtOnly))

        assertEquals(createdAtOnly, selected)
    }

    @Test
    fun a_fallback_list_with_only_drafts_and_prereleases_has_no_latest() {
        val draft = release(id = 2, tag = "v2.0.0", isDraft = true)
        val prerelease = release(id = 1, tag = "v1.0.0", isPrerelease = true)

        assertNull(selectLatestPublishedRelease(listOf(draft, prerelease)))
    }

    private fun release(
        id: Long,
        tag: String,
        publishedAt: String? = "2026-01-01T00:00:00Z",
        createdAt: String? = null,
        isDraft: Boolean = false,
        isPrerelease: Boolean = false,
    ): ReleaseNetwork =
        ReleaseNetwork(
            id = id,
            tagName = tag,
            draft = isDraft,
            prerelease = isPrerelease,
            publishedAt = publishedAt,
            createdAt = createdAt,
            tarballUrl = "https://example.test/$tag.tar.gz",
            zipballUrl = "https://example.test/$tag.zip",
            htmlUrl = "https://example.test/releases/$tag",
            assets = emptyList(),
        )
}

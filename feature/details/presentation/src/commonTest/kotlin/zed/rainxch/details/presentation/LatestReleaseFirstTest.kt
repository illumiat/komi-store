package zed.rainxch.details.presentation

import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.details.domain.model.ReleaseCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LatestReleaseFirstTest {
    @Test
    fun the_full_list_replaces_the_latest_only_release_and_keeps_the_selection() {
        val latest = release(id = 3, tag = "v1.2.0")
        val fullList = listOf(latest, release(id = 2, tag = "v1.1.0"), release(id = 1, tag = "v1.0.0"))

        val choice =
            chooseReleaseListChoice(
                previousSelected = latest,
                previousCategory = ReleaseCategory.STABLE,
                incoming = fullList,
                failed = false,
            )

        assertNotNull(choice)
        // The full list replaces the one-item latest-first list...
        assertEquals(fullList, choice.releases)
        // ...without moving the release already on screen.
        assertEquals(latest, choice.selected)
        assertEquals(ReleaseCategory.STABLE, choice.category)
    }

    @Test
    fun the_selection_falls_back_when_the_full_list_no_longer_ships_it() {
        val latest = release(id = 9, tag = "v9.0.0")
        val fullList = listOf(release(id = 2, tag = "v1.1.0"), release(id = 1, tag = "v1.0.0"))

        val choice =
            chooseReleaseListChoice(
                previousSelected = latest,
                previousCategory = ReleaseCategory.STABLE,
                incoming = fullList,
                failed = false,
            )

        assertNotNull(choice)
        assertEquals(fullList, choice.releases)
        assertEquals(2L, choice.selected.id)
        assertEquals(ReleaseCategory.STABLE, choice.category)
    }

    @Test
    fun a_failed_or_empty_full_read_keeps_the_latest_only_state() {
        val latest = release(id = 3, tag = "v1.2.0")
        val fullList = listOf(latest, release(id = 2, tag = "v1.1.0"))

        // Null means "nothing to apply": the caller leaves the latest-only state untouched.
        assertNull(
            chooseReleaseListChoice(latest, ReleaseCategory.STABLE, incoming = null, failed = true),
        )
        assertNull(
            chooseReleaseListChoice(latest, ReleaseCategory.STABLE, incoming = emptyList(), failed = false),
        )
        assertNull(
            chooseReleaseListChoice(latest, ReleaseCategory.STABLE, incoming = fullList, failed = true),
        )
    }

    @Test
    fun the_selection_is_carried_by_tag_when_the_release_id_changed() {
        val latest = release(id = 3, tag = "v1.2.0")
        val republished = release(id = 30, tag = "v1.2.0")
        val fullList = listOf(republished, release(id = 2, tag = "v1.1.0"))

        val choice =
            chooseReleaseListChoice(
                previousSelected = latest,
                previousCategory = ReleaseCategory.STABLE,
                incoming = fullList,
                failed = false,
            )

        assertNotNull(choice)
        assertEquals(republished, choice.selected)
    }

    @Test
    fun a_full_list_of_pre_releases_switches_the_selected_category() {
        val latest = release(id = 9, tag = "v1.0.0")
        val preRelease = release(id = 10, tag = "v2.0.0-beta.1", isPrerelease = true)
        val fullList = listOf(preRelease)

        val choice =
            chooseReleaseListChoice(
                previousSelected = latest,
                previousCategory = ReleaseCategory.STABLE,
                incoming = fullList,
                failed = false,
            )

        assertNotNull(choice)
        assertEquals(preRelease, choice.selected)
        assertEquals(ReleaseCategory.PRE_RELEASE, choice.category)
    }

    private fun release(
        id: Long,
        tag: String,
        isPrerelease: Boolean = false,
    ): GithubRelease =
        GithubRelease(
            id = id,
            tagName = tag,
            name = null,
            publishedAt = "2026-01-01T00:00:00Z",
            description = null,
            assets = emptyList(),
            tarballUrl = "",
            zipballUrl = "",
            htmlUrl = "https://example.test/releases/$tag",
            isPrerelease = isPrerelease,
        )
}

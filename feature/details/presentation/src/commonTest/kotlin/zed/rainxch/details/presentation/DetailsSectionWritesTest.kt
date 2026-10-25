package zed.rainxch.details.presentation

import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.GithubUser
import zed.rainxch.core.domain.model.account.github.GithubUserProfile
import zed.rainxch.core.domain.model.system.SystemArchitecture
import zed.rainxch.details.domain.model.RepoStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DetailsSectionWritesTest {
    @Test
    fun each_section_writes_only_its_own_fields() {
        val repoId = 7L
        val release = release(id = 3)
        val painted =
            RawDetailsState()
                .withHeaderFirstPaint(repo(repoId), sectionsDeferred = false)
                .copy(
                    allReleases = listOf(release),
                    selectedRelease = release,
                    areSectionsPending = false,
                )

        val withStats = painted.withStats(repoId, stats(stars = 12))

        assertEquals(stats(stars = 12), withStats.stats)
        assertNull(withStats.userProfile)
        assertNull(withStats.readmeMarkdown)
        assertEquals(listOf(release), withStats.allReleases)
        assertFalse(withStats.areSectionsPending)

        val withProfile = withStats.withUserProfile(repoId, profile(login = "dev"))

        assertEquals(profile(login = "dev"), withProfile.userProfile)
        assertEquals(stats(stars = 12), withProfile.stats)
        assertNull(withProfile.readmeMarkdown)
        assertEquals(listOf(release), withProfile.allReleases)

        val withReadme = withProfile.withReadme(repoId, markdown = "# Read me", language = "de")

        assertEquals("# Read me", withReadme.readmeMarkdown)
        assertEquals("de", withReadme.readmeLanguage)
        assertEquals(stats(stars = 12), withReadme.stats)
        assertEquals(profile(login = "dev"), withReadme.userProfile)
        assertEquals(listOf(release), withReadme.allReleases)
    }

    @Test
    fun a_failed_section_read_leaves_the_previous_value_alone() {
        val repoId = 7L
        val painted =
            RawDetailsState()
                .withHeaderFirstPaint(repo(repoId), sectionsDeferred = false)
                .withReadme(repoId, "# Old", "de")
                .withStats(repoId, stats(stars = 1))
                .withUserProfile(repoId, profile(login = "old"))

        assertSame(painted, painted.withStats(repoId, stats = null))
        assertSame(painted, painted.withUserProfile(repoId, profile = null))
        assertSame(painted, painted.withReadme(repoId, markdown = null, language = "de"))
        assertEquals("# Old", painted.readmeMarkdown)
        assertEquals(1, painted.stats?.stars)
        assertEquals("old", painted.userProfile?.login)
    }

    @Test
    fun a_section_read_for_another_repository_is_dropped() {
        val painted =
            RawDetailsState()
                .withHeaderFirstPaint(repo(7), sectionsDeferred = false)
                .withReadme(7, "# Old", "de")
                .withStats(7, stats(stars = 1))
                .withUserProfile(7, profile(login = "old"))
                .copy(isRetryingReleases = true)

        assertSame(painted, painted.withStats(99, stats(stars = 2)))
        assertSame(painted, painted.withUserProfile(99, profile(login = "new")))
        assertSame(painted, painted.withReadme(99, "# New", "fr"))
        assertSame(painted, painted.withEnvironment(99, environment()))
        assertSame(painted, painted.withSectionsSettled(99))

        assertEquals(1, painted.stats?.stars)
        assertEquals("old", painted.userProfile?.login)
        assertEquals("# Old", painted.readmeMarkdown)
        assertTrue(painted.areSectionsPending)
        assertTrue(painted.isRetryingReleases)
    }

    @Test
    fun the_environment_and_install_flags_ride_in_one_write() {
        val painted =
            RawDetailsState()
                .withHeaderFirstPaint(repo(7), sectionsDeferred = false)
                .withReadme(7, "# Read me", "de")

        val withEnvironment = painted.withEnvironment(7, environment())

        assertEquals(SystemArchitecture.AARCH64, withEnvironment.systemArchitecture)
        assertTrue(withEnvironment.isObtainiumAvailable)
        assertTrue(withEnvironment.isObtainiumEnabled)
        assertTrue(withEnvironment.isAppManagerAvailable)
        assertTrue(withEnvironment.isAppManagerEnabled)
        assertEquals("de", withEnvironment.deviceLanguageCode)
        assertTrue(withEnvironment.isComingFromUpdate)
        // The same write may not touch what the sections own.
        assertEquals(painted.repository, withEnvironment.repository)
        assertEquals("# Read me", withEnvironment.readmeMarkdown)
        assertNull(withEnvironment.stats)
        assertNull(withEnvironment.userProfile)
    }

    @Test
    fun settling_the_page_clears_the_pending_flags_and_keeps_the_sections() {
        val settled =
            RawDetailsState()
                .withHeaderFirstPaint(repo(7), sectionsDeferred = false)
                .withReadme(7, "# Read me", "de")
                .withStats(7, stats(stars = 4))
                .withUserProfile(7, profile(login = "dev"))
                .copy(isRetryingReleases = true)

        val after = settled.withSectionsSettled(7)

        assertFalse(after.areSectionsPending)
        assertFalse(after.isRetryingReleases)
        // A README, stats or profile that is still landing keeps its own row.
        assertEquals("# Read me", after.readmeMarkdown)
        assertEquals("de", after.readmeLanguage)
        assertEquals(4, after.stats?.stars)
        assertEquals("dev", after.userProfile?.login)
        assertEquals(settled.repository, after.repository)
    }

    private fun repo(id: Long): GithubRepoSummary =
        GithubRepoSummary(
            id = id,
            name = "repo-$id",
            fullName = "owner/repo-$id",
            owner = GithubUser(id = 1, login = "owner", avatarUrl = "", htmlUrl = ""),
            description = null,
            defaultBranch = "main",
            htmlUrl = "https://example.test/owner/repo-$id",
            stargazersCount = 0,
            forksCount = 0,
            language = null,
            topics = null,
            releasesUrl = "",
            updatedAt = "2026-01-01T00:00:00Z",
        )

    private fun release(id: Long): GithubRelease =
        GithubRelease(
            id = id,
            tagName = "v1.0.$id",
            name = null,
            publishedAt = "2026-01-01T00:00:00Z",
            description = null,
            assets = emptyList(),
            tarballUrl = "",
            zipballUrl = "",
            htmlUrl = "",
        )

    private fun stats(stars: Int): RepoStats =
        RepoStats(
            stars = stars,
            forks = 0,
            openIssues = 0,
        )

    private fun profile(login: String): GithubUserProfile =
        GithubUserProfile(
            id = 1,
            login = login,
            name = null,
            bio = null,
            avatarUrl = "https://example.test/$login.png",
            htmlUrl = "https://example.test/$login",
            followers = 0,
            following = 0,
            publicRepos = 0,
            location = null,
            company = null,
            blog = null,
            twitterUsername = null,
        )

    private fun environment(): DetailsEnvironment =
        DetailsEnvironment(
            systemArchitecture = SystemArchitecture.AARCH64,
            isObtainiumAvailable = true,
            isObtainiumEnabled = true,
            isAppManagerAvailable = true,
            isAppManagerEnabled = true,
            deviceLanguageCode = "de",
            isComingFromUpdate = true,
        )
}

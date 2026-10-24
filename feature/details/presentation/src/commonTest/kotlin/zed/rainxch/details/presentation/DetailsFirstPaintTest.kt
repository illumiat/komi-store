package zed.rainxch.details.presentation

import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.GithubUser
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DetailsFirstPaintTest {
    @Test
    fun the_first_paint_shows_the_repository_before_any_section_is_read() {
        val repo = repo(id = 7)

        val state =
            RawDetailsState()
                .withHeaderFirstPaint(repository = repo, sectionsDeferred = false)

        assertEquals(repo, state.repository)
        assertFalse(state.isLoading)
        assertTrue(state.areSectionsPending)
        assertNull(state.errorMessage)
        // Nothing the header does not need has been read yet, and nothing is invented for it.
        assertTrue(state.allReleases.isEmpty())
        assertNull(state.selectedRelease)
        assertTrue(state.installableAssets.isEmpty())
        assertNull(state.primaryAsset)
        assertNull(state.readmeMarkdown)
        assertNull(state.stats)
        assertNull(state.userProfile)
        assertNull(state.installedApp)
        assertTrue(state.installedApps.isEmpty())
        assertFalse(state.isFavourite)
        assertFalse(state.isStarred)
    }

    @Test
    fun the_first_paint_keeps_the_deferred_sections_decision() {
        val state =
            RawDetailsState()
                .withHeaderFirstPaint(repository = repo(id = 7), sectionsDeferred = true)

        assertTrue(state.sectionsDeferred)
    }

    @Test
    fun a_slow_favourite_or_star_read_lands_on_the_painted_header() {
        val repo = repo(id = 7)
        val painted = RawDetailsState().withHeaderFirstPaint(repo, sectionsDeferred = false)

        val favourited = painted.withFavourite(repo.id, true)
        val starred = painted.withStarred(repo.id, true)

        assertTrue(favourited.isFavourite)
        assertEquals(repo, favourited.repository)
        assertFalse(favourited.isStarred)
        assertTrue(starred.isStarred)
        assertEquals(repo, starred.repository)
        assertFalse(starred.isFavourite)
    }

    @Test
    fun a_favourite_read_that_hit_a_rate_limit_does_not_claim_the_repository_is_favourite() {
        val state =
            RawDetailsState()
                .withHeaderFirstPaint(repository = repo(id = 7), sectionsDeferred = false)
                .withFavourite(repoId = 7, isFavourite = null)

        assertFalse(state.isFavourite)
    }

    @Test
    fun a_late_result_from_another_repository_cannot_touch_the_painted_header() {
        val repo = repo(id = 7)
        val painted = RawDetailsState().withHeaderFirstPaint(repo, sectionsDeferred = false)

        val otherRepoWrite =
            painted
                .withFavourite(repoId = 99, isFavourite = true)
                .withStarred(repoId = 99, isStarred = true)
                .withInstalledAppState(
                    repoId = 99,
                    installedApps = listOf(installedApp("dev.example.other")),
                    installedApp = installedApp("dev.example.other"),
                )

        assertSame(painted, otherRepoWrite)
        assertEquals(repo, otherRepoWrite.repository)
        assertFalse(otherRepoWrite.isFavourite)
        assertFalse(otherRepoWrite.isStarred)
        assertNull(otherRepoWrite.installedApp)
    }

    @Test
    fun a_finished_sync_refreshes_only_the_installed_app_state() {
        val repo = repo(id = 7)
        val release = release(id = 1)
        val asset = asset(name = "app-arm64-v8a.apk")
        val painted =
            RawDetailsState()
                .withHeaderFirstPaint(repo, sectionsDeferred = false)
                .copy(
                    allReleases = listOf(release),
                    selectedRelease = release,
                    primaryAsset = asset,
                    readmeMarkdown = "# Read me",
                )
        val installed = installedApp("dev.example.app")

        val refreshed = painted.withInstalledAppState(repo.id, listOf(installed), installed)

        assertEquals(listOf(installed), refreshed.installedApps)
        assertEquals(installed, refreshed.installedApp)
        // The sync must not re-fetch, clear or re-select anything else on the page.
        assertEquals(painted.allReleases, refreshed.allReleases)
        assertEquals(painted.selectedRelease, refreshed.selectedRelease)
        assertEquals(painted.primaryAsset, refreshed.primaryAsset)
        assertEquals(painted.readmeMarkdown, refreshed.readmeMarkdown)
        assertEquals(repo, refreshed.repository)
        assertTrue(refreshed.areSectionsPending)
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

    private fun asset(name: String): GithubAsset =
        GithubAsset(
            id = 1L,
            name = name,
            contentType = "application/vnd.android.package-archive",
            size = 1024L,
            downloadUrl = "https://example.test/$name",
        )

    private fun installedApp(packageName: String): InstalledApp =
        InstalledApp(
            packageName = packageName,
            repoId = 7L,
            repoName = "repo-7",
            repoOwner = "owner",
            repoOwnerAvatarUrl = "",
            repoDescription = null,
            primaryLanguage = null,
            repoUrl = "https://example.test/owner/repo-7",
            installedVersion = "v1.0.1",
            installedAssetName = "app-arm64-v8a.apk",
            installedAssetUrl = "https://example.test/app-arm64-v8a.apk",
            latestVersion = "v1.0.1",
            latestAssetName = "app-arm64-v8a.apk",
            latestAssetUrl = "https://example.test/app-arm64-v8a.apk",
            latestAssetSize = 1024L,
            appName = "Example",
            installSource = InstallSource.THIS_APP,
            installedAt = 0L,
            lastCheckedAt = 0L,
            lastUpdatedAt = 0L,
            isUpdateAvailable = false,
            signingFingerprint = null,
            systemArchitecture = "arm64-v8a",
            fileExtension = "apk",
        )
}

@file:OptIn(ExperimentalTime::class)

package zed.rainxch.core.data.repository

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import zed.rainxch.core.data.dto.GitHubStarredItem
import zed.rainxch.core.data.dto.parseStarredPage
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.dao.StarredRepoDao
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toEntity
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.gitHubJson
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.repository.StarredExclusionReason
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.StarredRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class StarredRepositoryImpl(
    private val starredRepoDao: StarredRepoDao,
    private val installedAppsDao: InstalledAppDao,
    private val platform: Platform,
    private val clientProvider: GitHubClientProvider,
    private val backendApiClient: zed.rainxch.core.data.network.BackendApiClient,
) : StarredRepository {
    private val httpClient: HttpClient get() = clientProvider.client

    companion object {
        private const val SYNC_THRESHOLD_MS = 24 * 60 * 60 * 1000L

        /**
         * Makes the starred endpoints answer with `{starred_at, repo}` entries instead of
         * bare repository objects. Without it `starred_at` is not part of the response at
         * all, so the stored star time was always null (#822).
         */
        private const val STARRED_MEDIA_TYPE = "application/vnd.github.star+json"
    }

    override fun getAllStarred(): Flow<List<zed.rainxch.core.domain.model.repository.StarredRepository>> =
        starredRepoDao
            .getAllStarred()
            .map { it.map { entity -> entity.toDomain() } }

    override suspend fun isStarred(repoId: Long): Boolean = starredRepoDao.isStarred(repoId)

    override suspend fun setStarred(
        owner: String,
        repo: String,
        starred: Boolean,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    if (starred) {
                        httpClient.put("/user/starred/$owner/$repo") {
                            header("Content-Length", "0")
                        }
                    } else {
                        httpClient.delete("/user/starred/$owner/$repo")
                    }
                when {
                    response.status.isSuccess() -> {
                        Result.success(Unit)
                    }

                    response.status.value == 401 -> {
                        Result.failure(Exception("Authentication required. Please sign in with GitHub."))
                    }

                    else -> {
                        Result.failure(Exception("Failed to update star: ${response.status.description}"))
                    }
                }
            } catch (e: RateLimitException) {
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun fetchStarredForUsername(
        username: String,
    ): Result<List<zed.rainxch.core.domain.model.repository.StarredRepository>> =
        withContext(Dispatchers.IO) {
            try {
                val sanitized = username.trim().trimStart('@')
                if (sanitized.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Username is empty"))
                }

                val allRepos = mutableListOf<GitHubStarredItem>()
                var page = 1
                val perPage = 100

                while (true) {
                    val response =
                        httpClient.get("/users/$sanitized/starred") {
                            acceptGitHubMediaType(STARRED_MEDIA_TYPE)
                            parameter("per_page", perPage)
                            parameter("page", page)
                        }

                    if (!response.status.isSuccess()) {
                        val reason =
                            when (response.status.value) {
                                404 -> "User '$sanitized' not found."
                                403 -> "GitHub rate limit reached. Try again later or sign in."
                                else -> "Failed to fetch stars: ${response.status.description}"
                            }
                        return@withContext Result.failure(Exception(reason))
                    }

                    val repos = parseStarredPage(gitHubJson, response.body())
                    if (repos.isEmpty()) break
                    allRepos.addAll(repos)
                    if (repos.size < perPage) break
                    page++
                }

                warnIfTheMediaTypeWasIgnored(allRepos)
                val now = Clock.System.now().toEpochMilliseconds()
                val results =
                    coroutineScope {
                        val semaphore = Semaphore(25)
                        allRepos
                            .map { item ->
                                val repo = item.repo
                                async {
                                    semaphore.withPermit {
                                        buildRow(
                                            item = item,
                                            installedPackageNameOrNull =
                                                installedAppsDao
                                                    .getAppsByRepoId(repo.id)
                                                    .firstOrNull { !it.isPendingInstall }
                                                    ?.packageName,
                                            probe =
                                                checkForValidAssets(
                                                    repo.owner.login,
                                                    repo.name,
                                                    ::matchesAnyPlatform,
                                                ),
                                            now = now,
                                        )
                                    }
                                }
                            }.awaitAll()
                    }

                // The import flow advertises installable repos only, and it persists nothing,
                // so the "keep the row with a reason" rule does not apply here: an entry with
                // an exclusion reason has nothing to import and must not become a candidate.
                Result.success(results.filter { it.exclusionReason == null })
            } catch (e: RateLimitException) {
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Failed to fetch starred for username" }
                Result.failure(e)
            }
        }

    override suspend fun getLastSyncTime(): Long? = starredRepoDao.getLastSyncTime()

    override suspend fun needsSync(): Boolean {
        val lastSync = getLastSyncTime() ?: return true
        val now = Clock.System.now().toEpochMilliseconds()
        return (now - lastSync) > SYNC_THRESHOLD_MS
    }

    override suspend fun syncStarredRepos(forceRefresh: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (!forceRefresh && !needsSync()) {
                    return@withContext Result.success(Unit)
                }

                val allRepos = mutableListOf<GitHubStarredItem>()
                var page = 1
                val perPage = 100

                while (true) {
                    val response =
                        httpClient.get("/user/starred") {
                            acceptGitHubMediaType(STARRED_MEDIA_TYPE)
                            parameter("per_page", perPage)
                            parameter("page", page)
                        }

                    if (!response.status.isSuccess()) {
                        if (response.status.value == 401) {
                            return@withContext Result.failure(
                                Exception("Authentication required. Please sign in with GitHub."),
                            )
                        }
                        return@withContext Result.failure(
                            Exception("Failed to fetch starred repos: ${response.status.description}"),
                        )
                    }

                    val repos = parseStarredPage(gitHubJson, response.body())

                    if (repos.isEmpty()) break

                    allRepos.addAll(repos)

                    if (repos.size < perPage) break
                    page++
                }

                warnIfTheMediaTypeWasIgnored(allRepos)
                val now = Clock.System.now().toEpochMilliseconds()
                val starredRepos = mutableListOf<zed.rainxch.core.domain.model.repository.StarredRepository>()

                coroutineScope {
                    val semaphore = Semaphore(25)
                    val deferredResults =
                        allRepos.map { item ->
                            val repo = item.repo
                            async {
                                semaphore.withPermit {
                                    buildRow(
                                        item = item,
                                        installedPackageNameOrNull =
                                            installedAppsDao
                                                .getAppsByRepoId(repo.id)
                                                .firstOrNull { !it.isPendingInstall }
                                                ?.packageName,
                                        probe = checkForValidAssets(repo.owner.login, repo.name),
                                        now = now,
                                    )
                                }
                            }
                        }

                    deferredResults.awaitAll().let { allSynced ->
                        starredRepos.addAll(allSynced)
                    }
                }

                starredRepoDao.replaceAllStarred(starredRepos.map { it.toEntity() })

                Result.success(Unit)
            } catch (e: RateLimitException) {
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Failed to sync starred repos" }
                Result.failure(e)
            }
        }

    private fun matchesPlatform(assetName: String): Boolean {
        val name = assetName.lowercase()
        return when (platform) {
            Platform.ANDROID -> {
                name.endsWith(".apk")
            }

            Platform.WINDOWS -> {
                name.endsWith(".msi") || name.endsWith(".exe")
            }

            Platform.MACOS -> {
                name.endsWith(".dmg") || name.endsWith(".pkg")
            }

            Platform.LINUX -> {
                name.endsWith(".appimage") || name.endsWith(".deb") ||
                    name.endsWith(".rpm") || name.endsWith(".pkg.tar.zst")
            }
        }
    }

    private fun matchesAnyPlatform(assetName: String): Boolean {
        val name = assetName.lowercase()
        return name.endsWith(".apk") || name.endsWith(".msi") || name.endsWith(".exe") ||
            name.endsWith(".dmg") || name.endsWith(".pkg") || name.endsWith(".appimage") ||
            name.endsWith(".deb") || name.endsWith(".rpm") || name.endsWith(".pkg.tar.zst")
    }

    private suspend fun checkForValidAssets(
        owner: String,
        repo: String,
        matcher: (String) -> Boolean = ::matchesPlatform,
    ): AssetProbe {
        val releases = loadReleaseShapes(httpClient, backendApiClient, owner, repo)
        return if (releases == null) {
            AssetProbe.NotInstallable(StarredExclusionReason.PROBE_FAILED)
        } else {
            probeFor(releases, matcher)
        }
    }
}

/**
 * One release, reduced to what the installability decision needs.
 *
 * Both the backend and the direct GitHub path produce releases of different types but answer
 * the same three questions, so they are normalised here rather than duplicating the decision.
 */
internal data class ReleaseShape(
    val isStable: Boolean,
    val assetNames: List<String>,
    val tag: String?,
    val url: String?,
)

internal sealed interface AssetProbe {
    data class Installable(
        val version: String?,
        val url: String?,
    ) : AssetProbe

    data class NotInstallable(
        val reason: StarredExclusionReason,
    ) : AssetProbe
}

/**
 * Turns a release list into either the installable release or the reason there is none.
 *
 * Every branch that used to `return null` now names itself, which is what lets the sync keep
 * the repository and lets the screen explain why it is hidden (#1040).
 */
internal fun probeFor(
    releases: List<ReleaseShape>,
    matcher: (String) -> Boolean,
): AssetProbe {
    val stable = releases.firstOrNull { it.isStable }
    return when {
        releases.isEmpty() -> AssetProbe.NotInstallable(StarredExclusionReason.NO_RELEASES)
        stable == null -> AssetProbe.NotInstallable(StarredExclusionReason.PRERELEASE_ONLY)
        stable.assetNames.any(matcher) -> AssetProbe.Installable(stable.tag, stable.url)
        else -> AssetProbe.NotInstallable(StarredExclusionReason.NO_MATCHING_ASSET)
    }
}

/**
 * Parses a GitHub `starred_at` timestamp.
 *
 * The value is external input and the field is only newly populated, so it is parsed
 * defensively: a malformed entry must cost only its ordering hint. Throwing here would
 * happen inside the `async` that builds the row, cancel the enclosing `coroutineScope`, and
 * fail the whole sync - writing nothing at all.
 */
internal fun String?.toEpochMillisecondsOrNull(): Long? {
    val raw = this ?: return null
    return try {
        Instant.parse(raw).toEpochMilliseconds()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalArgumentException) {
        Logger.w(e) { "Unparsable starred_at '$raw'; treating the entry as oldest" }
        null
    }
}

/**
 * Sets an Accept media type, replacing whatever `defaultRequest` configured.
 *
 * Ktor's `header()` appends, and `HttpClientFactory.defaultRequest` already sends
 * `application/vnd.github+json`. Appending ours would put two Accept values on the request
 * and leave the server free to answer with the generic shape - which carries no
 * `starred_at`, i.e. exactly what this change exists to fix.
 */
private fun HttpRequestBuilder.acceptGitHubMediaType(mediaType: String) {
    headers.remove(HttpHeaders.Accept)
    header(HttpHeaders.Accept, mediaType)
}

/** Null means the probe itself failed, as opposed to "there is nothing installable". */
private suspend fun loadReleaseShapes(
    httpClient: HttpClient,
    backendApiClient: zed.rainxch.core.data.network.BackendApiClient,
    owner: String,
    repo: String,
): List<ReleaseShape>? =
    backendApiClient.getReleases(owner, repo, perPage = 10).fold(
        onSuccess = { releases -> releases.map { it.toReleaseShape() } },
        onFailure = { error ->
            if (zed.rainxch.core.data.network
                    .shouldFallbackToGithubOrRethrow(error)
            ) {
                fetchReleaseShapesFromGitHub(httpClient, owner, repo)
            } else {
                null
            }
        },
    )

/** How many releases to look at before deciding a repository is not installable. */
private const val RELEASES_PROBE_LIMIT = 10

private suspend fun fetchReleaseShapesFromGitHub(
    httpClient: HttpClient,
    owner: String,
    repo: String,
): List<ReleaseShape>? =
    try {
        val releasesResponse =
            httpClient.get("/repos/$owner/$repo/releases") {
                header("Accept", "application/vnd.github.v3+json")
                parameter("per_page", RELEASES_PROBE_LIMIT)
            }

        if (releasesResponse.status.isSuccess()) {
            val allReleases: List<GithubReleaseNetworkModel> = releasesResponse.body()
            allReleases.map { it.toReleaseShape() }
        } else {
            null
        }
    } catch (e: RateLimitException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (
        // Network boundary: Ktor surfaces IO, TLS, timeout and serialization failures through
        // a wide range of types, and here they all mean the same thing - the probe could not
        // conclude, which is a different answer from "nothing installable". Nothing above this
        // layer can act on the distinction.
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        Logger.w(e) { "Failed to check valid assets for $owner/$repo" }
        null
    }

/**
 * The star time is the whole point of asking for `star+json`. If an entire listing arrives
 * without one, the request went out with the generic media type (a proxy, or the default
 * Accept winning) and the sort has silently degraded to alphabetical - worth saying out loud
 * rather than rediscovering it from a bug report (#822).
 */
private fun warnIfTheMediaTypeWasIgnored(items: List<GitHubStarredItem>) {
    if (items.isNotEmpty() && items.all { it.starredAt == null }) {
        Logger.w {
            "Starred listing returned ${'$'}{items.size} entries without starred_at; " +
                "the star+json media type was not honoured, so ordering falls back to name"
        }
    }
}

private fun buildRow(
    item: GitHubStarredItem,
    installedPackageNameOrNull: String?,
    probe: AssetProbe,
    now: Long,
): zed.rainxch.core.domain.model.repository.StarredRepository {
    val repo = item.repo

    // when over the sealed result rather than two s? casts: a future probe variant must be
    // handled here. With casts it would fall through to "installable, no version" and be
    // persisted that way - precisely the silent failure this change removes.
    val version: String?
    val releaseUrl: String?
    val exclusionReason: StarredExclusionReason?
    when (probe) {
        is AssetProbe.Installable -> {
            version = probe.version
            releaseUrl = probe.url
            exclusionReason = null
        }

        is AssetProbe.NotInstallable -> {
            version = null
            releaseUrl = null
            exclusionReason = probe.reason
        }
    }

    return zed.rainxch.core.domain.model.repository.StarredRepository(
        repoId = repo.id,
        repoName = repo.name,
        repoOwner = repo.owner.login,
        repoOwnerAvatarUrl = repo.owner.avatarUrl,
        repoDescription = repo.description,
        primaryLanguage = repo.language,
        repoUrl = repo.htmlUrl,
        stargazersCount = repo.stargazersCount,
        forksCount = repo.forksCount,
        openIssuesCount = repo.openIssuesCount,
        isInstalled = installedPackageNameOrNull != null,
        installedPackageName = installedPackageNameOrNull,
        latestVersion = version,
        latestReleaseUrl = releaseUrl,
        starredAt = item.starredAt.toEpochMillisecondsOrNull(),
        addedAt = now,
        lastSyncedAt = now,
        exclusionReason = exclusionReason,
    )
}

@Serializable
private data class GithubReleaseNetworkModel(
    val assets: List<AssetNetworkModel>,
    val draft: Boolean? = null,
    val prerelease: Boolean? = null,
    @SerialName("tag_name") val tagName: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
private data class AssetNetworkModel(
    val name: String,
)

private fun GithubReleaseNetworkModel.toReleaseShape(): ReleaseShape =
    ReleaseShape(
        isStable = draft != true && prerelease != true,
        assetNames = assets.map { it.name },
        tag = tagName,
        url = htmlUrl,
    )

private fun zed.rainxch.core.data.dto.ReleaseNetwork.toReleaseShape(): ReleaseShape =
    ReleaseShape(
        isStable = draft != true && prerelease != true,
        assetNames = assets.map { it.name },
        tag = tagName,
        url = htmlUrl,
    )

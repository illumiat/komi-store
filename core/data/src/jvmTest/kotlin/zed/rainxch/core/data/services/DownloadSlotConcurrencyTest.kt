package zed.rainxch.core.data.services

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.installation.DownloadProgress
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.smart_detect.MatchingPreview
import zed.rainxch.core.domain.model.system.SystemArchitecture
import zed.rainxch.core.domain.network.AssetIdentity
import zed.rainxch.core.domain.network.DigestVerifier
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.network.SlowDownloadDetector
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.DownloadSpec
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.InstallPolicy
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.InstallerInfoExtractor
import zed.rainxch.core.domain.system.MultiSourceDownloader
import zed.rainxch.core.domain.system.PendingInstallNotifier
import zed.rainxch.core.domain.system.SystemInstallSerializer

/**
 * The orchestrator is documented to run at most [MAX_CONCURRENT] downloads at once. When many
 * downloads are enqueued together on a multi-threaded scope, a correct gate must never let more
 * than that many reach the [DownloadStage.Downloading] stage simultaneously.
 *
 * The blocking downloader below keeps every admitted download pinned in `Downloading` forever, so
 * the peak observed count is exactly the number of coroutines that slipped past the gate.
 */
class DownloadSlotConcurrencyTest {

    private val maxConcurrent = 3
    private val enqueued = 12

    @Test
    fun no_more_than_three_downloads_run_at_once_under_a_burst_of_enqueues() = runBlocking {
        val maxima = mutableListOf<Int>()
        val violations = mutableListOf<Int>()
        repeat(ROUNDS) { round ->
            val peak = runOneRound()
            maxima.add(peak)
            if (peak > maxConcurrent) violations.add(peak)
            println("round ${round + 1}: peak concurrent downloads = $peak")
        }

        assertTrue(
            violations.isEmpty(),
            "the slot gate must never admit more than $maxConcurrent concurrent downloads; " +
                "violating rounds showed $violations (peaks: $maxima)",
        )
    }

    private suspend fun runOneRound(): Int {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val peak = AtomicInteger(0)
        try {
            val orchestrator = newOrchestrator(scope)

            val sampler =
                scope.launch(Dispatchers.Default) {
                    orchestrator.downloads.collect { downloads ->
                        val active = downloads.values.count { it.stage == DownloadStage.Downloading }
                        peak.updateAndGet { maxOf(it, active) }
                    }
                }

            // Release every enqueue at once so the launched jobs contend for the gate together.
            val startGate = CountDownLatch(1)
            val launchers =
                (0 until enqueued).map { i ->
                    scope.launch(Dispatchers.IO) {
                        startGate.await(SETTLE_MILLIS, TimeUnit.MILLISECONDS)
                        orchestrator.enqueue(spec(i))
                    }
                }
            startGate.countDown()
            launchers.joinAll()

            // Let every contender reach the gate (or park in its poll loop) before sampling.
            delay(SETTLE_MILLIS)
            sampler.cancelAndJoin()
            return peak.get()
        } finally {
            scope.cancel()
        }
    }

    private fun newOrchestrator(scope: CoroutineScope) =
        DefaultDownloadOrchestrator(
            downloader = NoopDownloader(),
            multiSourceDownloader = PinningMultiSourceDownloader(),
            digestVerifier = NoopDigestVerifier(),
            installer = NoopInstaller(),
            installedAppsRepository = NoopInstalledAppsRepository(),
            pendingInstallNotifier = NoopPendingInstallNotifier(),
            slowDownloadDetector = NoopSlowDownloadDetector(),
            appScope = scope,
            systemInstallSerializer = NoopSystemInstallSerializer(),
            tokenStore = FakeTokenStore(),
        )

    private fun spec(index: Int) =
        DownloadSpec(
            packageName = "pkg.test.$index",
            repoOwner = "owner",
            repoName = "repo$index",
            asset =
                GithubAsset(
                    id = index.toLong(),
                    name = "app$index.apk",
                    contentType = "application/vnd.android.package-archive",
                    size = 100L,
                    downloadUrl = "https://example.invalid/app$index.apk",
                ),
            displayAppName = "App $index",
            installPolicy = InstallPolicy.DeferUntilUserAction,
            releaseTag = "v1",
        )

    private companion object {
        const val ROUNDS = 20
        const val SETTLE_MILLIS = 600L
    }
}

/** Emits one progress tick, then stays suspended forever so the slot is never given back. */
private class PinningMultiSourceDownloader : MultiSourceDownloader {
    override fun download(
        githubUrl: String,
        suggestedFileName: String?,
        identity: AssetIdentity?,
    ): Flow<DownloadProgress> =
        flow {
            emit(DownloadProgress(bytesDownloaded = 0L, totalBytes = null, percent = 0))
            awaitCancellation()
        }
}

private class NoopDownloader : Downloader {
    override fun download(
        url: String,
        suggestedFileName: String?,
        bypassMirror: Boolean,
        identity: AssetIdentity?,
    ): Flow<DownloadProgress> = emptyFlow()

    override suspend fun saveToFile(url: String, suggestedFileName: String?): String = ""

    override suspend fun getDownloadedFilePath(fileName: String): String? = null

    override suspend fun cancelDownload(fileName: String): Boolean = false

    override suspend fun discardPartial(fileName: String): Boolean = false

    override suspend fun reclaimOrphanedPartials(claimedNames: Set<String>): Int = 0
}

private class NoopDigestVerifier : DigestVerifier {
    override suspend fun verify(filePath: String, expectedDigest: String): String? = null
}

private class NoopInstaller : Installer {
    override suspend fun isSupported(extOrMime: String): Boolean = false

    override suspend fun ensurePermissionsOrThrow(extOrMime: String) = Unit

    override suspend fun install(filePath: String, extOrMime: String): InstallOutcome =
        InstallOutcome.COMPLETED

    override fun uninstall(packageName: String) = Unit

    override fun isAssetInstallable(assetName: String): Boolean = false

    override fun choosePrimaryAsset(assets: List<GithubAsset>): GithubAsset? = null

    override fun detectSystemArchitecture(): SystemArchitecture = SystemArchitecture.UNKNOWN

    override fun isObtainiumInstalled(): Boolean = false

    override fun openInObtainium(repoOwner: String, repoName: String, onOpenInstaller: () -> Unit) = Unit

    override fun isAppManagerInstalled(): Boolean = false

    override fun openInAppManager(filePath: String, onOpenInstaller: () -> Unit) = Unit

    override fun getApkInfoExtractor(): InstallerInfoExtractor =
        object : InstallerInfoExtractor {
            override suspend fun extractPackageInfo(filePath: String): ApkPackageInfo? = null
        }

    override fun openApp(packageName: String): Boolean = false

    override fun openWithExternalInstaller(filePath: String) = Unit
}

private class NoopSlowDownloadDetector : SlowDownloadDetector {
    override val suggestMirror: Flow<Unit> = emptyFlow()

    override suspend fun onProgress(progress: DownloadProgress) = Unit

    override suspend fun reset() = Unit
}

private class NoopPendingInstallNotifier : PendingInstallNotifier {
    override fun notifyPending(
        packageName: String,
        repoOwner: String,
        repoName: String,
        appName: String,
        versionTag: String,
    ) = Unit

    override fun clearPending(packageName: String) = Unit
}

private class NoopSystemInstallSerializer : SystemInstallSerializer {
    override suspend fun awaitFreeAndMarkPending(packageName: String, timeoutMs: Long) = Unit

    override fun markCompleted(packageName: String) = Unit
}

private class NoopInstalledAppsRepository : InstalledAppsRepository {
    override fun getAllInstalledApps(): Flow<List<InstalledApp>> = emptyFlow()

    override fun getAppsWithUpdates(): Flow<List<InstalledApp>> = emptyFlow()

    override fun getUpdateCount(): Flow<Int> = flowOf(0)

    override suspend fun getAppByPackage(packageName: String): InstalledApp? = null

    override suspend fun getAppByRepoId(repoId: Long): InstalledApp? = null

    override fun getAppByRepoIdAsFlow(repoId: Long): Flow<InstalledApp?> = flowOf(null)

    override suspend fun getAppsByRepoId(repoId: Long): List<InstalledApp> = emptyList()

    override fun getAppsByRepoIdAsFlow(repoId: Long): Flow<List<InstalledApp>> = emptyFlow()

    override suspend fun isAppInstalled(repoId: Long): Boolean = false

    override suspend fun saveInstalledApp(app: InstalledApp) = Unit

    override suspend fun deleteInstalledApp(packageName: String) = Unit

    override suspend fun checkForUpdates(packageName: String): Boolean = false

    override suspend fun checkAllForUpdates() = Unit

    override suspend fun updateAppVersion(
        packageName: String,
        newTag: String,
        newReleaseId: Long?,
        newAssetId: Long?,
        newAssetDigest: String?,
        newAssetName: String?,
        newAssetUrl: String?,
        newVersionName: String,
        newVersionCode: Long,
        signingFingerprint: String?,
        isPendingInstall: Boolean,
    ) = Unit

    override suspend fun clearInstallBinding(packageName: String) = Unit

    override suspend fun updateApp(app: InstalledApp) = Unit

    override suspend fun updateInstalledVersion(
        packageName: String,
        installedVersion: String,
        installedVersionName: String?,
        installedVersionCode: Long,
        isUpdateAvailable: Boolean,
    ) = Unit

    override suspend fun updatePendingStatus(packageName: String, isPending: Boolean) = Unit

    override suspend fun setIncludePreReleases(packageName: String, enabled: Boolean) = Unit

    override suspend fun setUpdateCheckEnabled(packageName: String, enabled: Boolean) = Unit

    override suspend fun setAssetFilter(
        packageName: String,
        regex: String?,
        fallbackToOlderReleases: Boolean,
    ) = Unit

    override suspend fun setPreferredVariant(
        packageName: String,
        variant: String?,
        tokens: String?,
        glob: String?,
        pickedIndex: Int?,
        siblingCount: Int?,
    ) = Unit

    override suspend fun clearPreferredVariant(packageName: String) = Unit

    override suspend fun setSkippedReleaseTag(packageName: String, tag: String?) = Unit

    override fun getAppsWithSkippedReleaseTag(): Flow<List<InstalledApp>> = emptyFlow()

    override suspend fun setPendingInstallFilePath(
        packageName: String,
        path: String?,
        version: String?,
        assetName: String?,
    ) = Unit

    override suspend fun previewMatchingAssets(
        owner: String,
        repo: String,
        regex: String?,
        includePreReleases: Boolean,
        fallbackToOlderReleases: Boolean,
    ): MatchingPreview = MatchingPreview(release = null, matchedAssets = emptyList())

    override suspend fun <R> executeInTransaction(block: suspend () -> R): R = block()
}

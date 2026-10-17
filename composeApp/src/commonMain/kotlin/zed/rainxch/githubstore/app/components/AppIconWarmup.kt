package zed.rainxch.githubstore.app.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.koin.compose.koinInject
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.presentation.components.AppIconCache
import zed.rainxch.core.presentation.components.rememberAppIconCache

/**
 * How many icons to warm. The library grid shows roughly this many tiles at once; warming further
 * would spend the same disk reads on rows that are not on screen yet.
 */
private const val WARMUP_ICON_LIMIT = 24

/**
 * Warms the icon cache as the installed-app list changes, so the library grid composes against
 * resolved icons instead of drawing placeholders on its first frame.
 *
 * This lives at the composition root on purpose. [AppIconCache.observe] can only serve what has
 * already been resolved, so warming from inside the library screen would run in the same frame the
 * tiles request their icons - too late to be the first frame. The root is composed from launch and
 * outlives every tab, so the icons are ready well before anyone navigates there.
 *
 * Keys mirror exactly what the tiles compose, including `pendingInstallFilePath`: the grid passes
 * that path for pending installs, and a warmed `null`-path key would be a different entry the tile
 * never reads - one that cannot resolve anyway, since a pending package has no launcher icon yet.
 *
 * Every emission is warmed, not just the first non-empty one. The list starts empty and fills in
 * after a sync, so the first non-empty emission can be a single app; warming once would leave the
 * rest of the library cold for the rest of the process. Repeated calls are cheap because
 * [AppIconCache.prefetch] skips keys that are cached or already resolving.
 */
@Composable
fun AppIconWarmup() {
    val cache = rememberAppIconCache()
    val installedAppsRepository: InstalledAppsRepository = koinInject()

    LaunchedEffect(cache, installedAppsRepository) {
        installedAppsRepository.getAllInstalledApps().collect { installed ->
            if (installed.isEmpty()) return@collect
            cache.prefetch(
                installed
                    .take(WARMUP_ICON_LIMIT)
                    .map { app ->
                        AppIconCache.Key(
                            packageName = app.packageName,
                            apkFilePath = app.pendingInstallFilePath,
                        )
                    },
            )
        }
    }
}

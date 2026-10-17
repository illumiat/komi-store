package zed.rainxch.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.flow.Flow

/**
 * Process-wide cache of application icons.
 *
 * The interface is deliberately small: read what is already cached, ask for a resolution, and watch
 * a single icon. Everything about *how* an icon is obtained - which platform API, on which thread,
 * how duplicates are collapsed - lives in the implementation.
 *
 * Icons are keyed by [Key]. [Key] includes the APK path because a package that is still pending
 * installation has no launcher icon yet: its icon has to be read out of the downloaded archive,
 * which is a different (and much heavier) lookup than querying the package manager.
 *
 * Instances are cached per [android.content.Context]; see the platform implementations.
 */
interface AppIconCache {
    data class Key(
        val packageName: String,
        val apkFilePath: String?,
    )

    /** The icon if it has already been resolved, otherwise `null`. Never blocks. */
    fun get(key: Key): ImageBitmap?

    /**
     * Watches one icon. Emits the cached value immediately when present, otherwise starts a
     * resolution and emits the result when it arrives. Concurrent callers asking for the same key
     * share a single resolution.
     */
    fun observe(key: Key): Flow<ImageBitmap?>

    /**
     * Starts resolving [keys] ahead of anyone watching them, so that a list which is about to be
     * shown composes against a warm cache.
     *
     * [observe] can only serve what is already resolved; without this, the first frame of a list
     * draws placeholders and the icons arrive a few frames later. Callers should pass keys in the
     * order the list renders them and cap the count to about one screenful - prefetching what is
     * not on screen spends the same disk reads for no visible benefit.
     *
     * Best-effort: duplicates, cached keys and keys already resolving are skipped, and a failure
     * here changes nothing that [observe] would not have done on its own.
     */
    fun prefetch(keys: List<Key>)
}

/**
 * Returns the icon cache for this process.
 *
 * Icons are held as [ImageBitmap]s, which cannot be stored in saved instance state, so the cache
 * has to outlive composition and the enclosing activity.
 */
@Composable
fun rememberAppIconCache(): AppIconCache {
    val context = LocalPlatformContext.current
    return remember(context) { appIconCache(context) }
}

/** Platform entry point for [rememberAppIconCache]. */
expect fun appIconCache(context: PlatformContext): AppIconCache

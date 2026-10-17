package zed.rainxch.core.presentation.components

import androidx.compose.ui.graphics.ImageBitmap
import coil3.PlatformContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Desktop has no equivalent of the Android package manager, so [InstalledAppIcon] falls back to the
 * bundled generic icon and there is nothing to resolve. The cache still exists so that callers can
 * use one code path on every platform.
 */
private object NoOpAppIconCache : AppIconCache {
    override fun get(key: AppIconCache.Key): ImageBitmap? = null

    override fun observe(key: AppIconCache.Key): Flow<ImageBitmap?> = flowOf(null)

    override fun prefetch(keys: List<AppIconCache.Key>) = Unit
}

actual fun appIconCache(context: PlatformContext): AppIconCache = NoOpAppIconCache

package zed.rainxch.core.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.app_icon

@Composable
actual fun InstalledAppIcon(
    packageName: String,
    appName: String,
    modifier: Modifier,
    apkFilePath: String?,
) {
    val cache = rememberAppIconCache()
    val key = remember(packageName, apkFilePath) { AppIconCache.Key(packageName, apkFilePath) }
    // observe() hands back a Flow, and collectAsState keys its internal effect on that instance -
    // so building a fresh one per recomposition would cancel and restart collection every time.
    val iconFlow = remember(key) { cache.observe(key) }
    // Hoisted: an argument expression runs on every recomposition, and cache.get takes the cache
    // lock each time, for a value produceState only consumes when its State is first created.
    val initialIcon = remember(key) { cache.get(key) }
    val iconBitmap by iconFlow.collectAsState(initial = initialIcon)

    val resolved = iconBitmap
    if (resolved != null) {
        Image(
            bitmap = resolved,
            contentDescription = appName,
            modifier = modifier,
        )
    } else {
        Image(
            painter = painterResource(Res.drawable.app_icon),
            contentDescription = appName,
            modifier = modifier,
        )
    }
}

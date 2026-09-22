package zed.rainxch.core.presentation.components.refresh

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import zed.rainxch.core.presentation.utils.isPullToRefreshSupported

/**
 * Lets a strip that cannot scroll on its own — a bar pinned above a list — drive an
 * ancestor [KomiPullToRefresh] the way the list below it does.
 *
 * `PullToRefreshBox` listens to nested scroll only, so a drag that starts on a surface with
 * no scrollable underneath never reaches it. That is what a header becomes once it is moved
 * out of the scroll container: pulling on the bar does nothing, while the same gesture on the
 * first card works. A scrollable that consumes nothing forwards the whole delta to its parent,
 * which is what the refresh connection is waiting for, and it leaves taps to the strip's own
 * children so the tabs stay usable.
 *
 * The gesture is Android-only in this app, and the rule lives here rather than at the call sites:
 * a caller cannot forget it, and no two of them end up spelling it differently.
 */
@Composable
fun Modifier.drivesPullToRefresh(): Modifier {
    // Unconditional on purpose — a conditional remember would have to be conditional everywhere
    // this is called, and isPullToRefreshSupported() is fixed per platform anyway.
    val noOpScroll = rememberScrollableState { 0f }
    return if (isPullToRefreshSupported()) {
        scrollable(state = noOpScroll, orientation = Orientation.Vertical)
    } else {
        this
    }
}

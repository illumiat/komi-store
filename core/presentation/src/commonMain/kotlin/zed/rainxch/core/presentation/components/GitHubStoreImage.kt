package zed.rainxch.core.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest

/**
 * The app's image component, shared by every screen.
 *
 * Deliberately on Coil directly rather than Landscapist's `CoilImage`, which this replaced.
 * That wrapper rebuilt its request and reset its state on **every** composition and always
 * emitted a Loading state before running the fetch, so an image already in the memory cache
 * still flashed a placeholder — and its `CrossfadePlugin` replayed a 250ms fade each time.
 * Neither is a cache problem; both are per-composition work the wrapper did.
 *
 * [imageModel]'s result is used as a `remember` key, so it must have value-based `equals`
 * — a `String` URL, or an `ImageRequest` the caller already built. A fresh object per
 * composition would key a new request each time, rebuild it, restart the fetch, and the
 * painter would cycle through Loading and may never settle.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GitHubStoreImage(
    imageModel: () -> Any?,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    // Kept at true so this change does not alter the other call sites' appearance: the
    // wrapper showed a spinner by default, and a large image fetched over the network
    // still wants one. Callers that sit on a tinted block pass false.
    showLoadingIndicator: Boolean = true,
    // Crop by default, and that is the icon case this component exists for: every one of its
    // call sites draws a small square slot (18dp list glyphs, 36dp sheet rows, 80/92dp
    // avatars), and icon art is meant to fill the slot. Markdown's images take the other
    // route — `MarkdownImageTransformer` uses Fit — because a document image must be seen
    // whole. So the two defaults differ on purpose; what was wrong was that this one could
    // not be overridden at all, which would have cropped the first banner or social preview
    // silently and offered no way out.
    contentScale: ContentScale = ContentScale.Crop,
    // Null by default so no call site changes: images here are decorative glyphs in list
    // rows and avatars. Non-decorative callers (a social preview, a banner) pass a label so
    // screen readers can describe what the image is instead of skipping it as unlabelled.
    contentDescription: String? = null,
) {
    val model = imageModel()
    if (model == null) {
        Box(modifier = modifier)
        return
    }

    // rememberAsyncImagePainter is never handed the layout constraints — only AsyncImage
    // wires those in — so a bare model request falls back to the source size and an 18dp
    // list icon is decoded at full resolution. Resolving the constraints here keeps the
    // decode matched to the size the caller actually draws, and is a no-op for callers
    // whose modifier already fixes the size.
    val sizeResolver = rememberConstraintsSizeResolver()
    val platformContext = LocalPlatformContext.current
    val request =
        remember(model, sizeResolver) {
            ImageRequest
                .Builder(platformContext)
                .data(model)
                .size(sizeResolver)
                .build()
        }
    val painter = rememberAsyncImagePainter(model = request)
    // state is a StateFlow: reading it directly yields the value but never recomposes, so
    // the painter would sit at Empty forever and no image would ever appear.
    val state by painter.state.collectAsState()
    val resolvedModifier = modifier.then(sizeResolver)

    when (state) {
        is AsyncImagePainter.State.Success ->
            Image(
                painter = painter,
                contentDescription = contentDescription,
                modifier = resolvedModifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
            )

        is AsyncImagePainter.State.Error ->
            // Same warning glyph the Landscapist version drew, so a broken image still says
            // so rather than silently occupying space.
            Box(modifier = resolvedModifier, contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxSize(.5f),
                )
            }

        is AsyncImagePainter.State.Loading ->
            // Draws only the spinner, if one was asked for — no state reset, no fade, so a
            // cached image reappears on the next frame.
            Box(modifier = resolvedModifier, contentAlignment = Alignment.Center) {
                if (showLoadingIndicator) CircularWavyProgressIndicator()
            }

        is AsyncImagePainter.State.Empty ->
            // Empty: the request has not started yet. Drawing the indicator here made every
            // caller that kept the default showLoadingIndicator flash a spinner on the very
            // first frame, and again whenever a lazy list rebuilt the item.
            Box(modifier = resolvedModifier)
    }
}

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GitHubStoreImage(
    imageModel: () -> Any?,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    showLoadingIndicator: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val model = imageModel()
    if (model == null) {
        Box(modifier = modifier)
        return
    }

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
            Box(modifier = resolvedModifier, contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxSize(.5f),
                )
            }

        is AsyncImagePainter.State.Loading ->
            Box(modifier = resolvedModifier, contentAlignment = Alignment.Center) {
                if (showLoadingIndicator) DelayedLoadingIndicator()
            }

        is AsyncImagePainter.State.Empty ->
            Box(modifier = resolvedModifier)
    }
}

/** How long an image has to stay unresolved before the spinner is worth showing. */
private const val LOADING_INDICATOR_DELAY_MS = 200L

/**
 * Shows a progress spinner only once loading has dragged on for [LOADING_INDICATOR_DELAY_MS].
 *
 * The spinner is an infinite animation, and it is driven by the main frame clock. Lists ask for
 * many images at once, so an unconditional spinner would run one animation per visible tile whose
 * image has not arrived yet - most of which resolve well before the user would notice a gap. The
 * delay means fast loads, including every cache hit, produce no animation at all, while a genuinely
 * slow load still gets a spinner.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DelayedLoadingIndicator() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_INDICATOR_DELAY_MS)
        visible = true
    }
    if (visible) CircularWavyProgressIndicator()
}

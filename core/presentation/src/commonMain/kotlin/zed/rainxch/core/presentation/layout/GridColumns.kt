package zed.rainxch.core.presentation.layout

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max

/**
 * Hard ceiling on column count. A grid wider than this is not a layout we support — a content
 * width of a few thousand dp already tops out at ~13 columns (see [GridColumnsTest]), and an
 * unbounded width (e.g. a grid dropped into a horizontal scroll container, where
 * `BoxWithConstraints.maxWidth` is `Float.POSITIVE_INFINITY`) must never propagate `Infinity`
 * into `GridCells.Fixed`/`StaggeredGridCells.Fixed`. Clamping keeps the cell count finite and
 * bounds how wrong a degenerate input can get.
 */
private const val MAX_COLUMNS = 16

/**
 * Floor for the `(maxCardWidth + spacing)` denominator. Guards against division by zero or a
 * negative divisor when the spacing is misconfigured or the card width is non-positive.
 */
private const val MIN_POSITIVE = 1e-3f

fun gridColumnCount(
    contentWidthDp: Float,
    maxCardWidthDp: Float,
    spacingDp: Float = CardGridSpec.GridSpacing.value,
): Int {
    // Degenerate inputs must not yield Infinity/NaN or absurd counts:
    //  - an unbounded width (placed inside a horizontal scroll container) is meaningless to split;
    //  - a non-positive card width or a zero/negative divisor would divide by ~0.
    if (!contentWidthDp.isFinite() || contentWidthDp <= 0f) return 1
    if (!maxCardWidthDp.isFinite() || maxCardWidthDp <= 0f) return 1
    val denom = (maxCardWidthDp + spacingDp).coerceAtLeast(MIN_POSITIVE)
    val raw = ceil((contentWidthDp - spacingDp) / denom).toInt()
    return raw.coerceIn(1, MAX_COLUMNS)
}

object CardGridSpec {
    val InfoMaxCardWidth: Dp = 550.dp
    val GridSpacing: Dp = 10.dp
}

@Composable
fun rememberGridColumns(maxCardWidth: Dp): Int {
    var columns by remember { mutableStateOf(1) }
    BoxWithConstraints {
        val computed = gridColumnCount(
            contentWidthDp = maxWidth.value,
            maxCardWidthDp = maxCardWidth.value,
            spacingDp = CardGridSpec.GridSpacing.value,
        )
        // Only write when the value actually changes. The write happens inside BoxWithConstraints'
        // layout lambda (after composition); an unconditional assignment would invalidate the
        // remembered state — and every reader of `columns` — on every layout pass even when the
        // column count is unchanged.
        if (computed != columns) columns = computed
    }
    return columns
}

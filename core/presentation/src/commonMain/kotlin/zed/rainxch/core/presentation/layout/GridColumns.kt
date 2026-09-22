package zed.rainxch.core.presentation.layout

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * Hard ceiling on column count. A grid wider than this is not a layout we support — a content
 * width of a few thousand dp already tops out at ~13 columns (see [GridColumnsTest]), and an
 * unbounded width must never propagate an absurd cell count into the grid. Clamping bounds how
 * wrong a degenerate input can get.
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

/**
 * Cells that split the cross axis evenly so no card comes out wider than [maxCardWidth], for
 * [LazyVerticalGrid][androidx.compose.foundation.lazy.grid.LazyVerticalGrid] and
 * [LazyVerticalStaggeredGrid][androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid]
 * respectively. The two spell their one method differently — `List<Int>` against `IntArray` — and
 * a return type is not part of JVM overloading, so they cannot share an implementation even
 * though the arithmetic is [widthsFor]'s alone.
 *
 * The count comes from the width the grid is actually handed, at the moment the grid lays out —
 * not from a width measured on the side and written back into composition. That earlier shape
 * cost a frame: the grid composed with a guessed count, laid itself out with it, and only settled
 * on the real one once the measurement came back. On a wide screen that first frame is every card
 * stacked in one column, and on the screens that animate item placement the move is animated,
 * which reads as the cards jerking into place each time the screen is composed anew.
 *
 * The sizing rule is [gridColumnCount]'s applied to the real width, so the column count and card
 * width are what these screens already show — only the timing changes.
 */
@Stable
class WidthCappedGridCells(
    private val maxCardWidth: Dp,
    private val contentPaddingHorizontal: Dp = 0.dp,
) : GridCells {

    override fun Density.calculateCrossAxisCellSizes(
        availableSize: Int,
        spacing: Int,
    ): List<Int> =
        widthCappedCellWidths(this, availableSize, spacing, maxCardWidth, contentPaddingHorizontal)
            .toList()

    override fun equals(other: Any?): Boolean =
        other is WidthCappedGridCells &&
            other.maxCardWidth == maxCardWidth &&
            other.contentPaddingHorizontal == contentPaddingHorizontal

    override fun hashCode(): Int =
        31 * maxCardWidth.hashCode() + contentPaddingHorizontal.hashCode()
}

/**
 * The staggered-grid counterpart of [WidthCappedGridCells]; see its doc for why the two exist.
 */
@Stable
class WidthCappedStaggeredCells(
    private val maxCardWidth: Dp,
    private val contentPaddingHorizontal: Dp = 0.dp,
) : StaggeredGridCells {

    override fun Density.calculateCrossAxisCellSizes(
        availableSize: Int,
        spacing: Int,
    ): IntArray =
        widthCappedCellWidths(this, availableSize, spacing, maxCardWidth, contentPaddingHorizontal)

    override fun equals(other: Any?): Boolean =
        other is WidthCappedStaggeredCells &&
            other.maxCardWidth == maxCardWidth &&
            other.contentPaddingHorizontal == contentPaddingHorizontal

    override fun hashCode(): Int =
        31 * maxCardWidth.hashCode() + contentPaddingHorizontal.hashCode()
}

@Composable
fun rememberWidthCappedGridCells(
    maxCardWidth: Dp = CardGridSpec.InfoMaxCardWidth,
    contentPaddingHorizontal: Dp = 0.dp,
): GridCells = remember(maxCardWidth, contentPaddingHorizontal) {
    WidthCappedGridCells(maxCardWidth, contentPaddingHorizontal)
}

@Composable
fun rememberWidthCappedStaggeredCells(
    maxCardWidth: Dp = CardGridSpec.InfoMaxCardWidth,
    contentPaddingHorizontal: Dp = 0.dp,
): StaggeredGridCells = remember(maxCardWidth, contentPaddingHorizontal) {
    WidthCappedStaggeredCells(maxCardWidth, contentPaddingHorizontal)
}

/**
 * As many equal-width columns as fit without one exceeding [maxCardWidth].
 *
 * [availableSize] is the width inside the grid's content padding, and the padding is put back
 * before deciding the count: that is the width these screens have always split, so the count and
 * the card width come out as they did. `density` is px per dp, so dividing is exactly toDp(),
 * which keeps [gridColumnCount] the one sizing rule, pinned by GridColumnsTest.
 */
internal fun widthCappedCellWidths(
    density: Density,
    availableSize: Int,
    spacing: Int,
    maxCardWidth: Dp,
    contentPaddingHorizontal: Dp,
): IntArray {
    val columns = gridColumnCount(
        contentWidthDp = availableSize / density.density + contentPaddingHorizontal.value,
        maxCardWidthDp = maxCardWidth.value,
        spacingDp = spacing / density.density,
    )
    val total = availableSize - spacing * (columns - 1)
    val size = total / columns
    return IntArray(columns) { size }
}

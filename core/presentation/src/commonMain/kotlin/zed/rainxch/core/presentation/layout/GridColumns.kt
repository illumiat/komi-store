package zed.rainxch.core.presentation.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
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
    //  - a non-finite width or card width is meaningless to split;
    //  - a non-positive card width or a zero/negative divisor would divide by ~0;
    //  - a NaN spacing slips past `coerceAtLeast` below (every NaN comparison is false), so the
    //    denominator has to be filtered here rather than left to the clamp.
    //
    // A grid in a horizontal scroll container is measured as `Constraints.Infinity`, i.e.
    // `Int.MAX_VALUE` *pixels*, which reaches here as a large but finite dp value — the `isFinite`
    // guards do not see it. The ceiling below is what bounds that case.
    if (!contentWidthDp.isFinite() || contentWidthDp <= 0f) return 1
    if (!maxCardWidthDp.isFinite() || maxCardWidthDp <= 0f) return 1
    if (!spacingDp.isFinite()) return 1
    val denom = (maxCardWidthDp + spacingDp).coerceAtLeast(MIN_POSITIVE)
    val raw = ceil((contentWidthDp - spacingDp) / denom).toInt()
    return raw.coerceIn(1, MAX_COLUMNS)
}

object CardGridSpec {
    val InfoMaxCardWidth: Dp = 550.dp
    val GridSpacing: Dp = 10.dp

    /**
     * The horizontal gap every card grid must use. The column count is decided from
     * [GridSpacing] while the cell widths are cut from the gap the grid actually passes, so
     * the two agree only while a screen spaces its cards by this value. Handing out the
     * arrangement — rather than letting each call site write `spacedBy(…)` itself — is what
     * keeps them from parting company.
     */
    val GridArrangement: Arrangement.Horizontal = Arrangement.spacedBy(GridSpacing)

    /** The vertical counterpart, for the staggered grids' `verticalItemSpacing`. */
    val GridItemSpacing: Dp = GridSpacing
}

/**
 * Cells that split the cross axis evenly, keeping a card as close to [maxCardWidth] as a whole
 * number of columns allows. It is a cap the count aims at, not one it can always honour: at 570dp
 * against a 550dp cap one column of 570dp still beats two of 280dp, so a card can come out as much
 * as two spacings over. See [GridColumnsTest]'s anchor cases.
 *
 * For [LazyVerticalGrid][androidx.compose.foundation.lazy.grid.LazyVerticalGrid] and
 * [LazyVerticalStaggeredGrid][androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid]
 * respectively. The two spell their one method differently — `List<Int>` against `IntArray` — and
 * a return type is not part of JVM overloading, so they cannot share a declaration even though the
 * arithmetic is [widthCappedCellWidths]'s alone.
 *
 * The count comes from the width the grid is actually handed, at the moment the grid lays out —
 * not from a width measured on the side and written back into composition. That earlier shape cost
 * a frame: the grid composed with a guessed count, laid itself out with it, and only settled on the
 * real one once the measurement came back. On a wide screen that first frame is every card stacked
 * in one column, and on the screens that animate item placement the move is animated, which reads
 * as the cards jerking into place each time the screen is composed anew.
 *
 * [contentPaddingHorizontal] is **per side**, exactly what `PaddingValues(horizontal = …)` means,
 * so a call site hands over the same number it writes into the grid's `contentPadding` and nothing
 * else. The card grids pad both sides alike. It is also the one value that has to track that
 * padding: if one moves without the other the column count drifts. Keep the declaration inside the
 * container the grid itself sits in (a `ScrollbarContainer` insets its content on desktop), so
 * what is measured here is what the grid measures.
 */
@Stable
data class WidthCappedGridCells(
    val maxCardWidth: Dp,
    val contentPaddingHorizontal: Dp = 0.dp,
) : GridCells {

    override fun Density.calculateCrossAxisCellSizes(
        availableSize: Int,
        spacing: Int,
    ): List<Int> =
        widthCappedCellWidths(this, availableSize, spacing, maxCardWidth, contentPaddingHorizontal)
            .toList()
}

/**
 * The staggered-grid counterpart of [WidthCappedGridCells]; see its doc for why the two exist.
 */
@Stable
data class WidthCappedStaggeredCells(
    val maxCardWidth: Dp,
    val contentPaddingHorizontal: Dp = 0.dp,
) : StaggeredGridCells {

    override fun Density.calculateCrossAxisCellSizes(
        availableSize: Int,
        spacing: Int,
    ): IntArray =
        widthCappedCellWidths(this, availableSize, spacing, maxCardWidth, contentPaddingHorizontal)
}

@Composable
fun rememberWidthCappedGridCells(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    maxCardWidth: Dp = CardGridSpec.InfoMaxCardWidth,
): GridCells {
    val horizontal = contentPadding.calculateStartPadding(LocalLayoutDirection.current)
    return remember(maxCardWidth, horizontal) {
        WidthCappedGridCells(maxCardWidth, horizontal)
    }
}

@Composable
fun rememberWidthCappedStaggeredCells(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    maxCardWidth: Dp = CardGridSpec.InfoMaxCardWidth,
): StaggeredGridCells {
    val horizontal = contentPadding.calculateStartPadding(LocalLayoutDirection.current)
    return remember(maxCardWidth, horizontal) {
        WidthCappedStaggeredCells(maxCardWidth, horizontal)
    }
}

/**
 * As many equal-width columns as fit while keeping a card as near [maxCardWidth] as a whole number
 * of columns allows. It is a target cap, not a hard one: as [WidthCappedGridCells]'s doc sets out, a
 * card can come out as much as two spacings over it.
 *
 * [availableSize] is what the grid has left for cells inside its content padding; the gaps
 * between cells are taken out of it below, and the horizontal content padding — both sides of it —
 * goes back on before the count is decided: that is the width these screens have always split, so
 * the count and the card widths come out as they did. It goes back on in pixels, the way the grid took it off, because an exact dp here would
 * disagree with the grid's own round-to-px at a density that is not a clean multiple and quietly
 * move the width across a column boundary. `density` is px per dp, so dividing is exactly toDp(),
 * which keeps [gridColumnCount] the one sizing rule, pinned by GridColumnsTest.
 *
 * The count is decided with [CardGridSpec.GridSpacing] rather than the gap the grid passes, which
 * is what the earlier measurement did; the widths below are still cut from the real gap. The two
 * only part company where a screen stops spacing its cards by [CardGridSpec.GridSpacing].
 *
 * That premise is now enforced rather than left to each call site: a grid takes its gap from
 * [CardGridSpec.GridArrangement] (with [CardGridSpec.GridItemSpacing] as the staggered vertical
 * counterpart), so no screen can hand over a gap of its own and drive the two apart. Changing the
 * gap means changing [CardGridSpec.GridSpacing] alone — the column count and the cell widths both
 * follow it.
 */
internal fun widthCappedCellWidths(
    density: Density,
    availableSize: Int,
    spacing: Int,
    maxCardWidth: Dp,
    contentPaddingHorizontal: Dp,
): IntArray {
    val paddingPx = with(density) { contentPaddingHorizontal.roundToPx() }
    val columns = gridColumnCount(
        contentWidthDp = (availableSize + paddingPx * 2) / density.density,
        maxCardWidthDp = maxCardWidth.value,
    )
    val total = availableSize - spacing * (columns - 1)
    val size = total / columns
    val remainder = total % columns
    // The leftover pixels go to the leading columns, which is how `GridCells.Fixed` splits the
    // same space (its `calculateCellsCrossAxisSizeImpl` adds one to a column while its index is
    // below the remainder). Dropping the remainder instead would shave a pixel off each leading
    // column and change the card widths these screens were approved with.
    //
    // The count comes from the width with the padding put back, while these widths come from the
    // pixels the grid handed over — a padding large enough to separate the two, or the column
    // ceiling kicking in, can drive `total` to zero or below. Clamp at zero rather than at one:
    // one pixel each would make the row `columns + spacing*(columns-1)` wide and overflow the
    // grid, clipping the trailing cards. A degenerate input gets empty columns; it must not get
    // a row wider than the space it was given.
    return IntArray(columns) { i -> (size + if (i < remainder) 1 else 0).coerceAtLeast(0) }
}

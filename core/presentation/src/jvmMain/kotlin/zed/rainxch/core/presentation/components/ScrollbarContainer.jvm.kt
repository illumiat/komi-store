@file:Suppress("DEPRECATION")

package zed.rainxch.core.presentation.components

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridLayoutInfo
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun ScrollbarContainer(
    listState: LazyListState,
    enabled: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    ScrollbarContainerShell(
        enabled = enabled,
        modifier = modifier,
        content = content,
    ) {
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
            style = scrollbarStyle(),
        )
    }
}

@Composable
actual fun ScrollbarContainer(
    gridState: LazyStaggeredGridState,
    enabled: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    ScrollbarContainerShell(
        enabled = enabled,
        modifier = modifier,
        content = content,
    ) {
        VerticalScrollbar(
            adapter = remember(gridState) { StaggeredGridScrollbarAdapter(gridState) },
            modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
            style = scrollbarStyle(),
        )
    }
}

@Composable
actual fun ScrollbarContainer(
    gridState: LazyGridState,
    enabled: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    ScrollbarContainerShell(
        enabled = enabled,
        modifier = modifier,
        content = content,
    ) {
        VerticalScrollbar(
            adapter = remember(gridState) { GridScrollbarAdapter(gridState) },
            modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
            style = scrollbarStyle(),
        )
    }
}

/**
 * The shell the three [ScrollbarContainer] overloads shared verbatim: the disabled fast path, the
 * content box, the scrollbar and its style. Only the adapter differs, so it is passed in and
 * created where the old bodies created it — after `content()`.
 */
@Composable
private fun ScrollbarContainerShell(
    enabled: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
    scrollbar: @Composable BoxScope.() -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier) {
            content()
        }
        return
    }
    // The scrollbar itself is a slot rather than a passed-in adapter: Compose offers two
    // unrelated ScrollbarAdapter contracts (foundation and foundation.v2) and the three callers
    // split across them — one lambda type cannot carry both.
    Box(modifier = modifier.padding(start = 8.dp)) {
        content()
        scrollbar()
    }
}

@Composable
private fun scrollbarStyle(): androidx.compose.foundation.ScrollbarStyle =
    LocalScrollbarStyle.current.copy(
        shape = RoundedCornerShape(32.dp),
        unhoverColor = MaterialTheme.colorScheme.onSurface,
        hoverColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

/**
 * The mapping between a lazy grid's scroll position and the scrollbar's offset, shared by both
 * grid flavours. They differ only in how they estimate the content height, and a copy that
 * drifts would put the thumb and the grid out of step — which is what the two directions being
 * written independently had already done: the offset was derived as `index/total * contentSize`
 * while the inverse scaled a *fraction of the scrollable range* by `total - 1`, so a drag landed
 * a couple of items away from where the pointer was (up to four in a 100-item, 3-lane grid), and
 * the further down the grid, the further off it got.
 *
 * Both directions now share one scale: the average pixel height of an item. That is what makes
 * them inverses, so a drag settles on the item it was dragged to.
 */
private class GridScrollbarAdapterBase(
    private val totalItemsCount: () -> Int,

    /** Index of the first visible item, or null when nothing is laid out yet. */
    private val firstVisibleIndex: () -> Int?,

    /** How far that item is scrolled past the viewport edge, in pixels. */
    private val firstVisibleOffsetY: () -> Int,

    /** The flavour's content height estimate; pure, which is what lets it be cached below. */
    private val estimateContentSize: () -> Float,

    private val scrollToItem: suspend (Int) -> Unit,

    /** Applies a raw pixel delta on the main axis; the flavour's `dispatchRawDelta`. */
    private val scrollByPixels: (Float) -> Unit,

    /** Identity of the measure result the estimate is derived from, for that cache. */
    private val measureResult: () -> Any,
) : ScrollbarAdapter {
    // `scrollOffset`/`maxScrollOffset` are polled repeatedly while the scrollbar is hovered or
    // dragged. A measure result is immutable and is only replaced by the next measure, so the
    // estimate is a pure function of it and does not need to be re-derived for the same one.
    private var cachedMeasureResult: Any? = null
    private var cachedContentSize = 0f

    private fun estimatedContentSize(): Float {
        val key = measureResult()
        if (key === cachedMeasureResult) return cachedContentSize
        val size = estimateContentSize()
        cachedMeasureResult = key
        cachedContentSize = size
        return size
    }

    private val averageItemSize: Float
        get() {
            val count = totalItemsCount()
            return if (count > 0) estimatedContentSize() / count else 0f
        }

    override val scrollOffset: Float
        get() {
            val index = firstVisibleIndex() ?: return 0f
            return index * averageItemSize - firstVisibleOffsetY()
        }

    override fun maxScrollOffset(containerSize: Int): Float = (estimatedContentSize() - containerSize).coerceAtLeast(0f)

    override suspend fun scrollTo(
        containerSize: Int,
        scrollOffset: Float,
    ) {
        // The inverse of `scrollOffset` above: an offset is turned back into an item through the
        // same per-item scale it was built from, then the leftover fraction of an item is applied
        // in pixels. `scrollToItem` alone can only land on an item's start, so it discards that
        // fraction; and because the thumb is positioned from the `scrollOffset` read back after
        // every drag sample, discarding it makes the thumb jump in whole-item steps and lag the
        // pointer by up to half an item.
        val itemSize = averageItemSize
        if (itemSize <= 0f) return
        val exactIndex = scrollOffset / itemSize
        // No "empty list" guard is needed on top of the one above: with no items,
        // averageItemSize is 0 and it has already returned.
        val targetIndex = exactIndex.toInt().coerceIn(0, totalItemsCount() - 1)
        scrollToItem(targetIndex)
        // The fraction of an item the snap above left behind. A positive delta scrolls forward,
        // pushing the item's start above the viewport edge by exactly this remainder — which is
        // what `scrollOffset` (index - firstVisibleOffsetY) reads back as the target offset.
        val remainder = scrollOffset - targetIndex * itemSize
        if (remainder != 0f) {
            scrollByPixels(remainder)
        }
    }
}

private fun StaggeredGridScrollbarAdapter(
    gridState: LazyStaggeredGridState,
): GridScrollbarAdapterBase =
    GridScrollbarAdapterBase(
        totalItemsCount = { gridState.layoutInfo.totalItemsCount },
        firstVisibleIndex = { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index },
        firstVisibleOffsetY = { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.offset?.y ?: 0 },
        estimateContentSize = { estimateStaggeredGridContentSize(gridState.layoutInfo) },
        scrollToItem = { gridState.scrollToItem(it) },
        scrollByPixels = { delta -> gridState.dispatchRawDelta(delta) },
        measureResult = { gridState.layoutInfo },
    )

private fun GridScrollbarAdapter(
    gridState: LazyGridState,
): GridScrollbarAdapterBase =
    GridScrollbarAdapterBase(
        totalItemsCount = { gridState.layoutInfo.totalItemsCount },
        firstVisibleIndex = { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index },
        firstVisibleOffsetY = { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.offset?.y ?: 0 },
        estimateContentSize = { estimateLazyGridContentSize(gridState.layoutInfo) },
        scrollToItem = { gridState.scrollToItem(it) },
        scrollByPixels = { delta -> gridState.dispatchRawDelta(delta) },
        measureResult = { gridState.layoutInfo },
    )

private fun estimateStaggeredGridContentSize(layoutInfo: LazyStaggeredGridLayoutInfo): Float {
    if (layoutInfo.totalItemsCount == 0) return 0f
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return 0f
    val avgHeight = visibleItems.map { it.size.height }.average().toFloat()
    val laneCount =
        maxOf(
            visibleItems.maxOf { it.lane + 1 },
            1,
        )
    val rows = (layoutInfo.totalItemsCount + laneCount - 1) / laneCount
    return rows * avgHeight + layoutInfo.beforeContentPadding + layoutInfo.afterContentPadding
}

/** One visible grid item reduced to the two numbers the height estimate reads. */
internal data class GridItemExtent(
    val height: Int,
    val span: Int,
)

/**
 * Estimate the full content height of a grid from the items currently on screen.
 *
 * Kept as arithmetic on plain numbers rather than on `LazyGridLayoutInfo` so the formula can be
 * pinned by a test; the adapter below is the only thing that reads the layout. See
 * [estimateLazyGridContentSize] for what the estimate assumes and where it is approximate.
 */
internal fun estimateGridContentHeight(
    totalItemsCount: Int,
    visibleItems: List<GridItemExtent>,
    lanes: Int,
    mainAxisItemSpacing: Int,
    contentPadding: Int,
): Float {
    if (totalItemsCount == 0) return 0f
    if (visibleItems.isEmpty()) return 0f
    // The grid's line count (e.g. 3 for `GridCells.Fixed(3)`). Do not derive it from the
    // visible window: a viewport showing only full-line items would report 1 and break the
    // math below.
    val laneCount = maxOf(lanes, 1)
    // A full-line item (`GridItemSpan(maxLineSpan)`, like the banners in AppsScreen) occupies a
    // whole line, not 1/lanes of one. Weight each visible item by its span so the line count we
    // extrapolate matches the grid: a normal item contributes 1/lanes of a line, a full-line
    // item a full line.
    //
    // The same weight has to reach the height below, or the two disagree and the estimate
    // inflates. Work the AppsScreen shape through — one full-line banner (40px over 3 lanes)
    // above three cards (200px each) in a 3-lane grid. The window holds two lines, 40px and
    // 200px, so the content is 240px. A per-item mean height gives (40 + 600) / 4 = 160, and
    // 2 lines × 160 = 320: a third too tall, which draws the thumb too short and lands the
    // content in the wrong place when the bar is dragged. Weighting by span/lanes gives
    // 40·(3/3) + 3 × 200·(1/3) = 240 over those two lines — 120 a line — and 2 × 120 = 240 is
    // the content exactly.
    //
    // The weight is exact whenever a line is fully tiled by the items in it, which is why the
    // uniform case also comes out right: 99 items over 3 lanes is 99 × 100 × 1/3 = 3300 across
    // 33 lines = 100 a line, the item height. What remains approximate is a partly-filled last
    // line, which the average down-weights — a far smaller error than reading a line count
    // against a per-item height, and the thumb only needs to be approximately right.
    var lineSpanSum = 0L
    var weightedHeightSum = 0f
    for (item in visibleItems) {
        lineSpanSum += item.span
        weightedHeightSum += item.height * (item.span.toFloat() / laneCount)
    }
    // Total lines the visible window occupies: a normal item = 1/lanes of a line, a full-line
    // item = 1 line.
    val linesOccupied = lineSpanSum.toFloat() / laneCount
    // Lines per ITEM, read off the window and applied to the whole list. This is the estimate's
    // one knowingly weak step, and it is worth being precise about why.
    //
    // The window is assumed to be a representative sample of the list. That holds while every
    // item occupies the same number of lines, and it is exact in both of the limits that matter:
    // a window of ordinary cards reports 1/lanes (99 items / 3 lanes = 33 lines), and a window of
    // full-line items reports 1 (6 banners = 6 lines). Both are covered by the tests.
    //
    // It stops holding where a screen mixes the two and clusters the full-line items — which is
    // exactly AppsScreen, whose banners and section headers sit at the top of the list and between
    // groups. Scrolling one into view raises the window's full-line share well above the list's,
    // so `linesPerItem` drifts, `estimatedLines` with it, and the thumb shifts under the pointer.
    //
    // No closed form fixes this, and it is not for want of trying: replacing the window average
    // with the ordinary item's exact 1/lanes over-corrects, because a window that *is* the whole
    // list should report the mixed answer — for one banner (40px) above three cards (200px) in a
    // 3-lane grid the true content is the two lines' 240px, which the average reproduces exactly
    // and 1/lanes would report as 160. Each formula is right at one end and wrong at the other,
    // and the window is the only thing that says which end it is at.
    //
    // Smoothing the value across frames would help — the drift is a rate, not an offset — but that
    // needs state this pure function deliberately does not have (see the note on
    // `GridContentHeightTest`), so it is left as a known approximation rather than traded for a
    // stateful estimator nobody asked for. The thumb only needs to be approximately right.
    val linesPerItem = linesOccupied / visibleItems.size
    val estimatedLines = totalItemsCount * linesPerItem
    // Height per LINE, from the span-weighted sum above — not a per-item mean, which would put
    // the two factors of `estimatedLines * avgLineHeight` in different units the moment the
    // window mixes spans. Guarded rather than assumed positive: a span of zero throughout would
    // leave the sum empty, and a non-finite value here propagates into the thumb's geometry.
    val avgLineHeight = if (linesOccupied > 0f) weightedHeightSum / linesOccupied else 0f
    // Rows are separated by mainAxisItemSpacing; with N estimated lines there are N-1 gaps
    // (zero when there are fewer than two lines). `mainAxisItemSpacing` is an Int in px — 0 when
    // no `Arrangement.spacedBy` is set — so a Dp.Unspecified concern does not apply here.
    val lineSpacing = if (estimatedLines > 1f) (estimatedLines - 1f) * mainAxisItemSpacing.toFloat() else 0f
    return estimatedLines * avgLineHeight + lineSpacing + contentPadding
}

private fun estimateLazyGridContentSize(layoutInfo: LazyGridLayoutInfo): Float =
    estimateGridContentHeight(
        totalItemsCount = layoutInfo.totalItemsCount,
        visibleItems = layoutInfo.visibleItemsInfo.map { GridItemExtent(it.size.height, it.span) },
        lanes = layoutInfo.maxSpan,
        mainAxisItemSpacing = layoutInfo.mainAxisItemSpacing,
        contentPadding = layoutInfo.beforeContentPadding + layoutInfo.afterContentPadding,
    )

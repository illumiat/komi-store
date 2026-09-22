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
import kotlin.math.roundToInt

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
        // same per-item scale it was built from.
        val itemSize = averageItemSize
        if (itemSize <= 0f) return
        // No "empty list" guard is needed on top of the one above: with no items,
        // averageItemSize is 0 and it has already returned.
        scrollToItem((scrollOffset / itemSize).roundToInt().coerceIn(0, totalItemsCount() - 1))
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

private fun estimateLazyGridContentSize(layoutInfo: LazyGridLayoutInfo): Float {
    if (layoutInfo.totalItemsCount == 0) return 0f
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return 0f
    // The grid's line count (e.g. 3 for `GridCells.Fixed(3)`). Do not derive it from the
    // visible window: a viewport showing only full-line items would report 1 and break the
    // math below.
    val lanes = maxOf(layoutInfo.maxSpan, 1)
    // A full-line item (`GridItemSpan(maxLineSpan)`, like the banners in AppsScreen) occupies a
    // whole line, not 1/lanes of one. Weight each visible item by its span so the line count we
    // extrapolate matches the grid: a normal item contributes 1/lanes of a line, a full-line
    // item a full line.
    var lineSpanSum = 0L
    var heightSum = 0L
    for (item in visibleItems) {
        lineSpanSum += item.span
        heightSum += item.size.height
    }
    // Total lines the visible window occupies: a normal item = 1/lanes of a line, a full-line
    // item = 1 line.
    val linesOccupied = lineSpanSum.toFloat() / lanes
    val linesPerItem = linesOccupied / visibleItems.size
    val estimatedLines = layoutInfo.totalItemsCount * linesPerItem
    // `avgLineHeight` is a per-ITEM mean and `estimatedLines` a LINE count, which reads like a unit
    // mismatch. It is not: with `estimatedLines = totalItemsCount * linesOccupied / n` the two
    // multiply out to `totalItemsCount * avgItemHeight / itemsPerLine`, a coherent estimator. The
    // uniform case checks out exactly — 33 lines * 100px = 3300 = 99 items * 100px / 3 per line.
    // The real approximation is that `linesOccupied` is fractional once spans are mixed (one
    // full-line item among single-lane ones reports 1.667 lines where two are visible), which
    // skews itemsPerLine. That is inherent to extrapolating from a window; no obvious formula
    // improves it, and the thumb only needs to be approximately right.
    val avgLineHeight = heightSum.toFloat() / visibleItems.size
    // Rows are separated by mainAxisItemSpacing; with N estimated lines there are N-1 gaps
    // (zero when there are fewer than two lines). `mainAxisItemSpacing` is an Int in px — 0 when
    // no `Arrangement.spacedBy` is set — so a Dp.Unspecified concern does not apply here.
    val lineSpacing = if (estimatedLines > 1f) (estimatedLines - 1f) * layoutInfo.mainAxisItemSpacing.toFloat() else 0f
    return estimatedLines * avgLineHeight + lineSpacing + layoutInfo.beforeContentPadding + layoutInfo.afterContentPadding
}

@file:Suppress("DEPRECATION")

package zed.rainxch.core.presentation.components

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
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
    if (!enabled) {
        Box(modifier = modifier) {
            content()
        }
        return
    }
    Box(modifier = modifier.padding(start = 8.dp)) {
        val scrollbarStyle =
            LocalScrollbarStyle.current.copy(
                shape = RoundedCornerShape(32.dp),
                unhoverColor = MaterialTheme.colorScheme.onSurface,
                hoverColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        content()
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier =
                Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd),
            style = scrollbarStyle,
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
    if (!enabled) {
        Box(modifier = modifier) {
            content()
        }

        return
    }
    Box(modifier = modifier.padding(start = 8.dp)) {
        val scrollbarStyle =
            LocalScrollbarStyle.current.copy(
                shape = RoundedCornerShape(32.dp),
                unhoverColor = MaterialTheme.colorScheme.onSurface,
                hoverColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )

        content()
        val adapter = remember(gridState) { StaggeredGridScrollbarAdapter(gridState) }
        VerticalScrollbar(
            adapter = adapter,
            modifier =
                Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd),
            style = scrollbarStyle,
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
    if (!enabled) {
        Box(modifier = modifier) {
            content()
        }

        return
    }
    Box(modifier = modifier.padding(start = 8.dp)) {
        val scrollbarStyle =
            LocalScrollbarStyle.current.copy(
                shape = RoundedCornerShape(32.dp),
                unhoverColor = MaterialTheme.colorScheme.onSurface,
                hoverColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )

        content()
        val adapter = remember(gridState) { GridScrollbarAdapter(gridState) }
        VerticalScrollbar(
            adapter = adapter,
            modifier =
                Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd),
            style = scrollbarStyle,
        )
    }
}

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
private abstract class GridScrollbarAdapterBase : ScrollbarAdapter {
    protected abstract val totalItemsCount: Int

    /** Index of the first visible item, or null when nothing is laid out yet. */
    protected abstract val firstVisibleIndex: Int?

    /** How far that item is scrolled past the viewport edge, in pixels. */
    protected abstract val firstVisibleOffsetY: Int

    protected abstract fun estimatedContentSize(): Float

    protected abstract suspend fun scrollToItem(index: Int)

    private val averageItemSize: Float
        get() {
            val count = totalItemsCount
            return if (count > 0) estimatedContentSize() / count else 0f
        }

    override val scrollOffset: Float
        get() {
            val index = firstVisibleIndex ?: return 0f
            return index * averageItemSize - firstVisibleOffsetY
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
        val lastIndex = totalItemsCount - 1
        if (lastIndex < 0) return
        scrollToItem((scrollOffset / itemSize).roundToInt().coerceIn(0, lastIndex))
    }
}

private class StaggeredGridScrollbarAdapter(
    private val gridState: LazyStaggeredGridState,
) : GridScrollbarAdapterBase() {
    private val firstVisible get() = gridState.layoutInfo.visibleItemsInfo.firstOrNull()

    override val totalItemsCount: Int get() = gridState.layoutInfo.totalItemsCount

    override val firstVisibleIndex: Int? get() = firstVisible?.index

    override val firstVisibleOffsetY: Int get() = firstVisible?.offset?.y ?: 0

    override suspend fun scrollToItem(index: Int) {
        gridState.scrollToItem(index)
    }

    override fun estimatedContentSize(): Float {
        val layoutInfo = gridState.layoutInfo
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
}

private class GridScrollbarAdapter(
    private val gridState: LazyGridState,
) : GridScrollbarAdapterBase() {
    private val firstVisible get() = gridState.layoutInfo.visibleItemsInfo.firstOrNull()

    override val totalItemsCount: Int get() = gridState.layoutInfo.totalItemsCount

    override val firstVisibleIndex: Int? get() = firstVisible?.index

    override val firstVisibleOffsetY: Int get() = firstVisible?.offset?.y ?: 0

    override suspend fun scrollToItem(index: Int) {
        gridState.scrollToItem(index)
    }

    private var cachedLayoutInfo: LazyGridLayoutInfo? = null
    private var cachedContentSize = 0f

    override fun estimatedContentSize(): Float {
        // `scrollOffset`/`maxScrollOffset` are polled repeatedly while the scrollbar is hovered or
        // dragged. A measure result is immutable and is only replaced by the next measure, so the
        // estimate is a pure function of it and does not need to be re-derived for the same one.
        val layoutInfo = gridState.layoutInfo
        if (layoutInfo === cachedLayoutInfo) return cachedContentSize
        val size = estimateContentSize(layoutInfo)
        cachedLayoutInfo = layoutInfo
        cachedContentSize = size
        return size
    }

    private fun estimateContentSize(layoutInfo: LazyGridLayoutInfo): Float {
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
        // Height of a line, so that `estimatedLines * avgLineHeight` is a height. Items sharing a
        // line share its height, so the average per-item height is the right estimator — dividing by
        // `linesOccupied` instead would multiply the line height by `lanes` and overestimate the
        // content by that factor on an ordinary grid (every item spanning one lane).
        val avgLineHeight = heightSum.toFloat() / visibleItems.size
        // Rows are separated by mainAxisItemSpacing; with N estimated lines there are N-1 gaps
        // (zero when there are fewer than two lines). `mainAxisItemSpacing` is an Int in px — 0 when
        // no `Arrangement.spacedBy` is set — so a Dp.Unspecified concern does not apply here.
        val lineSpacing = if (estimatedLines > 1f) (estimatedLines - 1f) * layoutInfo.mainAxisItemSpacing.toFloat() else 0f
        return estimatedLines * avgLineHeight + lineSpacing + layoutInfo.beforeContentPadding + layoutInfo.afterContentPadding
    }
}

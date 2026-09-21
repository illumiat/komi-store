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

private class StaggeredGridScrollbarAdapter(
    private val gridState: LazyStaggeredGridState,
) : ScrollbarAdapter {
    override val scrollOffset: Float
        get() {
            val layoutInfo = gridState.layoutInfo
            val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
            val fraction = firstVisible.index.toFloat() / maxOf(layoutInfo.totalItemsCount, 1)
            return fraction * estimatedContentSize() - firstVisible.offset.y.toFloat()
        }

    override fun maxScrollOffset(containerSize: Int): Float = (estimatedContentSize() - containerSize).coerceAtLeast(0f)

    override suspend fun scrollTo(
        containerSize: Int,
        scrollOffset: Float,
    ) {
        val totalContent = estimatedContentSize()
        val layoutInfo = gridState.layoutInfo
        val maxOffset = maxScrollOffset(containerSize)
        if (layoutInfo.totalItemsCount == 0 || totalContent <= 0f || maxOffset <= 0f) return
        val fraction = (scrollOffset / maxOffset).coerceIn(0f, 1f)

        val targetIndex =
            (fraction * (layoutInfo.totalItemsCount - 1))
                .toInt()
                .coerceIn(0, maxOf(layoutInfo.totalItemsCount - 1, 0))
        gridState.scrollToItem(targetIndex)
    }

    private fun estimatedContentSize(): Float {
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
) : ScrollbarAdapter {
    override val scrollOffset: Float
        get() {
            val layoutInfo = gridState.layoutInfo
            val firstVisible = layoutInfo.visibleItemsInfo.firstOrNull() ?: return 0f
            val fraction = firstVisible.index.toFloat() / maxOf(layoutInfo.totalItemsCount, 1)
            return fraction * estimatedContentSize() - firstVisible.offset.y.toFloat()
        }

    override fun maxScrollOffset(containerSize: Int): Float = (estimatedContentSize() - containerSize).coerceAtLeast(0f)

    override suspend fun scrollTo(
        containerSize: Int,
        scrollOffset: Float,
    ) {
        val totalContent = estimatedContentSize()
        val layoutInfo = gridState.layoutInfo
        val maxOffset = maxScrollOffset(containerSize)
        if (layoutInfo.totalItemsCount == 0 || totalContent <= 0f || maxOffset <= 0f) return
        val fraction = (scrollOffset / maxOffset).coerceIn(0f, 1f)

        val targetIndex =
            (fraction * (layoutInfo.totalItemsCount - 1))
                .toInt()
                .coerceIn(0, maxOf(layoutInfo.totalItemsCount - 1, 0))
        gridState.scrollToItem(targetIndex)
    }

    private var cachedLayoutInfo: LazyGridLayoutInfo? = null
    private var cachedContentSize = 0f

    private fun estimatedContentSize(): Float {
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
        val linesPerItem = (lineSpanSum.toFloat() / lanes) / visibleItems.size
        val estimatedLines = layoutInfo.totalItemsCount * linesPerItem
        val avgHeight = heightSum.toFloat() / visibleItems.size
        return estimatedLines * avgHeight + layoutInfo.beforeContentPadding + layoutInfo.afterContentPadding
    }
}

package zed.rainxch.core.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the scrollbar's grid height estimate.
 *
 * The estimator extrapolates the whole content height from the items on screen, and it is easy to
 * "simplify" into a wrong answer: an earlier revision averaged the visible item heights and
 * multiplied that per-item mean by a line count, which only balances while every item spans the
 * same number of lanes. The AppsScreen shape — a full-line banner above a row of cards — is where
 * that broke, so the mixed-span cases below carry the weight here.
 */
class GridContentHeightTest {

    private fun item(height: Int, span: Int) = GridItemExtent(height = height, span = span)

    /**
     * The estimate is built from float divisions, so a value that is exact in arithmetic lands a
     * few ulps off in `Float`. Compare with a tolerance rather than bit-for-bit; the tolerance is
     * far below one pixel, so it still pins the formula.
     */
    private fun assertEstimate(expected: Float, actual: Float) =
        assertEquals(expected, actual, absoluteTolerance = 0.01f)

    /** No items on screen: nothing to extrapolate from, and not a division by zero. */
    @Test
    fun emptyWindowEstimatesNothing() {
        assertEstimate(
            0f,
            estimateGridContentHeight(
                totalItemsCount = 12,
                visibleItems = emptyList(),
                lanes = 3,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            ),
        )
        assertEstimate(
            0f,
            estimateGridContentHeight(
                totalItemsCount = 0,
                visibleItems = listOf(item(200, 1)),
                lanes = 3,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            ),
        )
    }

    /**
     * The case the per-item mean got wrong by a third. One full-line banner (40px across all three
     * lanes) above three cards (200px each): two lines of 40 and 200, so 240px of content. The old
     * arithmetic reported 320.
     */
    @Test
    fun fullLineBannerAboveACardRowUsesTheWeightedLineHeight() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 4,
                visibleItems = listOf(
                    item(height = 40, span = 3),
                    item(height = 200, span = 1),
                    item(height = 200, span = 1),
                    item(height = 200, span = 1),
                ),
                lanes = 3,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            )
        assertEstimate(240f, height)
    }

    /** The same shape in a two-lane grid, which the old arithmetic overstated by ~22%. */
    @Test
    fun fullLineBannerInATwoLaneGridUsesTheWeightedLineHeight() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 4,
                visibleItems = listOf(
                    item(height = 30, span = 2),
                    item(height = 30, span = 2),
                    item(height = 100, span = 1),
                    item(height = 100, span = 1),
                ),
                lanes = 2,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            )
        assertEstimate(160f, height)
    }

    /**
     * Uniform grids must not regress: 99 items of 100px over three lanes is 33 lines of 100px.
     * Both the old and the new arithmetic are exact here, so this is the guard against a fix that
     * gives up the simple case.
     */
    @Test
    fun uniformGridKeepsTheExactAnswer() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 99,
                visibleItems = listOf(item(height = 100, span = 1), item(height = 100, span = 1)),
                lanes = 3,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            )
        assertEstimate(3300f, height)
    }

    /**
     * A window that shows only two of the three lanes still describes a full-width line: the two
     * items each hold a third of a line, which is the same weighting the line count uses, so the
     * nine-item grid is three lines of 200px.
     */
    @Test
    fun partiallyFilledWindowStillDescribesWholeLines() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 9,
                visibleItems = listOf(item(height = 200, span = 1), item(height = 200, span = 1)),
                lanes = 3,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            )
        assertEstimate(600f, height)
    }

    /** Full-line items only: each is a line of its own, whatever the lane count. */
    @Test
    fun fullLineItemsCountOneLineEach() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 6,
                visibleItems = listOf(item(height = 60, span = 3), item(height = 60, span = 3)),
                lanes = 3,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            )
        assertEstimate(360f, height)
    }

    /** A single-lane grid is the degenerate case of the same arithmetic. */
    @Test
    fun singleLaneGridIsExact() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 3,
                visibleItems = listOf(item(height = 50, span = 1)),
                lanes = 1,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            )
        assertEstimate(150f, height)
    }

    /** Main-axis spacing is added between estimated lines and not after the last one. */
    @Test
    fun spacingAppliesBetweenLinesAndNotAfterTheLast() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 9,
                visibleItems = listOf(item(height = 200, span = 1), item(height = 200, span = 1)),
                lanes = 3,
                mainAxisItemSpacing = 10,
                contentPadding = 0,
            )
        // Three lines, so two gaps of 10px: 600 + 20.
        assertEstimate(620f, height)
    }

    /** Content padding is added to the estimate as-is. */
    @Test
    fun contentPaddingIsIncluded() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 3,
                visibleItems = listOf(item(height = 50, span = 1)),
                lanes = 1,
                mainAxisItemSpacing = 0,
                contentPadding = 24,
            )
        assertEstimate(174f, height)
    }

    /**
     * A single line has no gap to add — the estimate must not place one before the first line or
     * after the only one.
     */
    @Test
    fun singleLineAddsNoSpacing() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 3,
                visibleItems = listOf(item(height = 50, span = 1), item(height = 50, span = 1)),
                lanes = 3,
                mainAxisItemSpacing = 10,
                contentPadding = 0,
            )
        assertEstimate(50f, height)
    }

    /** All-zero spans leave nothing to average; the estimate degrades instead of going non-finite. */
    @Test
    fun zeroSpansDoNotProduceANonFiniteEstimate() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 6,
                visibleItems = listOf(item(height = 200, span = 0)),
                lanes = 3,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            )
        assertEstimate(0f, height)
    }
}

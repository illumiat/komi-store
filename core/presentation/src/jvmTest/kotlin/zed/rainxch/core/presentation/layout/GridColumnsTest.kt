package zed.rainxch.core.presentation.layout

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

class GridColumnsTest {

    @Test
    fun columnCountMatchesFormulaForAnchorCases() {
        val cases = listOf(
            Triple(10, 270, 1),
            Triple(11, 270, 1),
            Triple(190, 270, 1),
            Triple(195, 270, 1),
            Triple(270, 270, 1),
            Triple(289, 550, 1),
            Triple(345, 550, 1),
            Triple(400, 270, 2),
            Triple(400, 550, 1),
            Triple(570, 550, 1),
            Triple(571, 550, 2),
            Triple(588, 550, 2),
            Triple(588, 270, 3),
            Triple(640, 550, 2),
            Triple(700, 550, 2),
            Triple(700, 270, 3),
            Triple(820, 550, 2),
            Triple(820, 270, 3),
            Triple(1280, 550, 3),
            Triple(1280, 270, 5),
            Triple(3440, 270, 13),
            Triple(3440, 550, 7),
        )
        for ((width, maxCard, expected) in cases) {
            assertEquals(
                expected,
                gridColumnCount(width.toFloat(), maxCard.toFloat()),
                "width=$width maxCard=$maxCard",
            )
        }
    }

    @Test
    fun widthAtOrBelowLowerBoundClampsToOne() {
        assertEquals(1, gridColumnCount(0f, 270f))
        assertEquals(1, gridColumnCount(-5f, 270f))
        assertEquals(1, gridColumnCount(10f, 270f))
        assertEquals(1, gridColumnCount(0f, 550f))
    }

    @Test
    fun degenerateInputsStayFiniteAndBounded() {
        // A non-finite width is meaningless to split, so it falls back to a single column. Only a
        // direct call can produce it: a grid inside a horizontal scroll container is measured as
        // `Constraints.Infinity`, i.e. `Int.MAX_VALUE` *pixels*, which reaches here as a large but
        // *finite* dp — the `isFinite` guard does not see it, and it is the column ceiling below
        // that bounds it instead.
        assertEquals(1, gridColumnCount(Float.POSITIVE_INFINITY, 270f))
        assertEquals(1, gridColumnCount(Float.NEGATIVE_INFINITY, 270f))

        // Non-positive card width (division by zero or a negative denominator) is guarded.
        assertEquals(1, gridColumnCount(400f, 0f))
        assertEquals(1, gridColumnCount(400f, -50f))

        // A pathological negative spacing must not divide by zero. The denominator is floored at
        // MIN_POSITIVE, which makes the quotient astronomically large, so what this pins is the
        // ceiling — a regression that stopped clamping would sail past 16 here instead.
        val negativeSpacing = gridColumnCount(400f, 270f, spacingDp = -1000f)
        assertEquals(16, negativeSpacing, "negativeSpacing=$negativeSpacing")

        // A grid in a horizontal scroll container arrives as Int.MAX_VALUE px — a huge but *finite*
        // dp value, so it is not the `isFinite` guard that bounds it but the ceiling, and this is
        // the path that case actually takes.
        val unboundedPixelWidth = gridColumnCount(Int.MAX_VALUE.toFloat(), 270f)
        assertEquals(16, unboundedPixelWidth, "unboundedPixelWidth=$unboundedPixelWidth")

        // An absurdly large but finite width is clamped to the column ceiling, not left unbounded.
        val hugeWidth = gridColumnCount(1_000_000f, 270f)
        assertEquals(16, hugeWidth)
    }

    @Test
    fun cellsReproduceTheColumnCountTheseScreensAlreadyHad() {
        // The grid hands over the width *inside* its content padding, so the padding goes back on
        // before the count is decided — the width these screens have always split. Without it the
        // count drops by one whenever a 560dp boundary lands inside the padding, which is a card
        // grid silently reshaping itself between releases. 1136dp region is exactly such a case.
        val density = Density(density = 1f, fontScale = 1f)
        val maxCardWidth = 550.dp
        // Per side, the same number the grids write into `contentPadding = PaddingValues(horizontal = 12.dp)`.
        val padding = 12.dp

        val regions = listOf(360, 561, 800, 1136, 1280, 1692, 2240, 3440)
        for (regionWidth in regions) {
            val availableSize = regionWidth - 2 * with(density) { padding.roundToPx() }
            val expected = gridColumnCount(regionWidth.toFloat(), maxCardWidth.value)

            val widths = widthCappedCellWidths(density, availableSize, 10, maxCardWidth, padding)

            assertEquals(expected, widths.size, "region=$regionWidth")
            // The leftover pixels go to the leading columns, exactly how `GridCells.Fixed` splits
            // the same space (`calculateCellsCrossAxisSizeImpl` adds one while the index is below
            // the remainder). Pinning "all columns equal" here instead would have pinned the bug —
            // a dropped remainder shaves a pixel off every leading column, which is a card width
            // these screens were never approved with.
            val gridWidth = availableSize - 10 * (widths.size - 1)
            val size = gridWidth / widths.size
            val remainder = gridWidth % widths.size
            widths.forEachIndexed { i, w ->
                assertEquals(size + if (i < remainder) 1 else 0, w, "region=$regionWidth col=$i of ${widths.toList()}")
            }
        }
    }

    @Test
    fun contentPaddingIsPutBackOnBothSides() {
        // A 12dp-per-side padding is put back as 24dp, so the count is decided over the same width
        // a grid with no padding and 24dp more room would see. The width is picked just below a
        // column boundary, so counting one side instead of two drops a column and fails here.
        val density = Density(density = 1f, fontScale = 1f)
        val padded = widthCappedCellWidths(density, availableSize = 1110, spacing = 10, maxCardWidth = 550.dp, contentPaddingHorizontal = 12.dp)
        val unpadded = widthCappedCellWidths(density, availableSize = 1134, spacing = 10, maxCardWidth = 550.dp, contentPaddingHorizontal = 0.dp)
        // Both are pinned to the literal expected count as well as to each other: comparing only
        // the two would let a common-mode error through (putting the padding back twice over
        // still agrees on 3 columns for both inputs).
        assertEquals(3, padded.size, "padded=${padded.toList()}")
        assertEquals(3, unpadded.size, "unpadded=${unpadded.toList()}")
    }

    @Test
    fun countMatchesTheRegionTheEarlierMeasurementSawAtEveryDensity() {
        // The grid hands over whole pixels with the content padding already taken out as whole
        // pixels, so the padding goes back the same way. Pin the column count against the width
        // the earlier BoxWithConstraints-based measurement read at the same density: at a density
        // that is not a clean multiple, adding the padding back as exact dp instead drifts from
        // that width by a fraction of a dp, which is enough to cross a column boundary.
        val paddingPerSide = 12.dp
        // Column boundaries sit at 560k+10dp, so a fraction of a dp of rounding only ever flips a
        // count there. Sample one pixel either side of each: an arbitrary width would leave the
        // assertion unable to fail no matter how far the reconstruction drifts.
        val boundaryWidthsDp = listOf(570f, 1130f, 1690f, 2250f)
        for (densityValue in listOf(1f, 1.5f, 2f, 2.125f, 2.625f, 2.75f, 3f, 3.5f)) {
            val density = Density(density = densityValue, fontScale = 1f)
            val paddingPx = with(density) { paddingPerSide.roundToPx() }
            for (boundaryDp in boundaryWidthsDp) {
                val atBoundary = (boundaryDp * densityValue).roundToInt()
                for (regionPx in listOf(atBoundary - 1, atBoundary, atBoundary + 1)) {
                    val availableSize = regionPx - 2 * paddingPx

                    val widths =
                        widthCappedCellWidths(density, availableSize, 10, 550.dp, paddingPerSide)

                    // What `BoxWithConstraints.maxWidth.value` read: the region's own width in dp.
                    assertEquals(
                        gridColumnCount(regionPx / densityValue, 550f),
                        widths.size,
                        "density=$densityValue regionPx=$regionPx boundary=$boundaryDp",
                    )
                }
            }
        }
    }
}

package zed.rainxch.core.presentation.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        // Unbounded width (e.g. a grid inside a horizontal scroll container) must not produce
        // Infinity/NaN or Int.MAX_VALUE — it falls back to a single column.
        assertEquals(1, gridColumnCount(Float.POSITIVE_INFINITY, 270f))
        assertEquals(1, gridColumnCount(Float.NEGATIVE_INFINITY, 270f))

        // Non-positive card width (division by zero or a negative denominator) is guarded.
        assertEquals(1, gridColumnCount(400f, 0f))
        assertEquals(1, gridColumnCount(400f, -50f))

        // A pathological negative spacing cannot drive the denominator to zero/negative; the
        // result stays finite, positive, and within the column ceiling.
        val negativeSpacing = gridColumnCount(400f, 270f, spacingDp = -1000f)
        assertTrue(negativeSpacing in 1..16, "negativeSpacing=$negativeSpacing")

        // An absurdly large but finite width is clamped to the column ceiling, not left unbounded.
        val hugeWidth = gridColumnCount(1_000_000f, 270f)
        assertEquals(16, hugeWidth)
    }
}

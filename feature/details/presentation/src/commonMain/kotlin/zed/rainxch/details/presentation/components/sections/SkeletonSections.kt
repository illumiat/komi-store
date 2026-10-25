package zed.rainxch.details.presentation.components.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.details.presentation.components.SkeletonBlock
import zed.rainxch.details.presentation.components.SkeletonLine
import zed.rainxch.details.presentation.components.lineHeightOf
import zed.rainxch.details.presentation.components.skeletonBlockColor

fun LazyListScope.skeletonSections() {
    item(key = "details-stats-skeleton") {
        StatsSkeleton()
    }
}

// sections/Stats.kt: a 20dp lead, the section title, then two rows of three tiles.
@Composable
private fun StatsSkeleton(modifier: Modifier = Modifier) {
    val personality = LocalPersonality.current
    val type = personality.type
    val block = skeletonBlockColor()
    val lineShape = RoundedCornerShape(personality.shape.cornerSmall)
    val tileShape = RoundedCornerShape(personality.shape.corner)
    val tileHeight = STAT_TILE_PADDING * 2 + lineHeightOf(type.labelSmall) + lineHeightOf(type.numeral)

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(SECTION_LEAD))
        SkeletonLine(type.sectionTitle, 0.35f, lineShape, block)
        Spacer(Modifier.height(STAT_GAP))
        repeat(2) { row ->
            if (row > 0) Spacer(Modifier.height(STAT_GAP))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(STAT_GAP),
            ) {
                repeat(3) {
                    SkeletonBlock(tileShape, block, Modifier.weight(1f).height(tileHeight))
                }
            }
        }
    }
}

private val SECTION_LEAD = 20.dp
private val STAT_GAP = 10.dp
private val STAT_TILE_PADDING = 10.dp

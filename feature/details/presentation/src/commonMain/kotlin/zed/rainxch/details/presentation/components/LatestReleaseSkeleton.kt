package zed.rainxch.details.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview

// Stands in for the version filter, the asset and version pickers and the install button while
// the latest release is being read. Its height matches those rows, so the page below the header
// does not move when the release lands.
@Composable
internal fun LatestReleaseSkeleton(modifier: Modifier = Modifier) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val shape = personality.shape
    // KomiChip is a Material filter chip in Classic (32dp) and 34dp tall in Manga.
    val chipHeight = if (personality is MangaPersonality) 34.dp else 32.dp
    val chipShape = RoundedCornerShape(shape.corner)
    val lineShape = RoundedCornerShape(shape.cornerSmall)
    val block = colors.surfaceContainerHigh.copy(alpha = skeletonAlpha())

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(chipShape, block, Modifier.width(64.dp).height(chipHeight))
            SkeletonBlock(chipShape, block, Modifier.width(96.dp).height(chipHeight))
            SkeletonBlock(chipShape, block, Modifier.width(56.dp).height(chipHeight))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // The assets picker: an overline plus a one-line row (36dp minimum, 20dp padding).
            SkeletonPicker(
                modifier = Modifier.weight(0.65f),
                rowHeight = 56.dp,
                lineShape = lineShape,
                rowShape = chipShape,
                block = block,
            )
            // The version picker also shows the release name under the version: two text lines.
            SkeletonPicker(
                modifier = Modifier.weight(0.35f),
                rowHeight = 60.dp,
                lineShape = lineShape,
                rowShape = chipShape,
                block = block,
            )
        }
        SkeletonBlock(chipShape, block, Modifier.fillMaxWidth().height(56.dp))
    }
}

@Composable
private fun SkeletonPicker(
    rowHeight: Dp,
    lineShape: Shape,
    rowShape: Shape,
    block: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SkeletonBlock(lineShape, block, Modifier.width(44.dp).height(16.dp))
        SkeletonBlock(rowShape, block, Modifier.fillMaxWidth().height(rowHeight))
    }
}

@Composable
private fun SkeletonBlock(
    shape: Shape,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clip(shape).background(color))
}

@Composable
private fun skeletonAlpha(): Float {
    val motion = LocalPersonality.current.motion
    if (!motion.animates) return 0.4f
    val transition = rememberInfiniteTransition(label = "latest-release-skeleton")
    val alpha by
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec =
                infiniteRepeatable(
                    animation = motion.ambient<Float>(),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "latest-release-skeleton-alpha",
        )
    return alpha
}

@Preview
@Composable
private fun LatestReleaseSkeletonPreview() {
    PersonalityPreview {
        LatestReleaseSkeleton()
    }
}

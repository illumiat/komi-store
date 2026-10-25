package zed.rainxch.details.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.components.surfaces.KomiScreentone
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfacePaper
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.spacing.Spacing

// One skeleton frame for the whole page. Every block takes the size of the real area it stands in
// for, from the same tokens, so the repository and the latest release are replaced in place and no
// second, differently shaped skeleton takes over in between.

// The AppHeader card while the repository is being read: its surface, padding, picture, title and
// footer rows, with each text line cut to the height of the role that fills it.
@Composable
internal fun DetailsHeaderSkeleton(modifier: Modifier = Modifier) {
    val personality = LocalPersonality.current
    val manga = personality is MangaPersonality
    val type = personality.type
    val block = skeletonBlockColor()
    val lineShape = RoundedCornerShape(personality.shape.cornerSmall)
    val boxShape = RoundedCornerShape(personality.shape.corner)
    val dockHeight = Spacing.sm * 2 + DOCK_ICON + Spacing.xs + lineHeightOf(type.labelSmall)
    // Manga's status sticker hangs off the panel's corner, so the real panel leaves room for it.
    val stickerRoom =
        if (manga) Modifier.padding(top = Spacing.md, end = Spacing.sm) else Modifier

    KomiSurface(
        modifier = modifier.fillMaxWidth().then(stickerRoom),
        paper = KomiSurfacePaper.Tinted,
        screentone = KomiScreentone.Corner,
        contentPadding = PaddingValues(Spacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                SkeletonBlock(boxShape, block, Modifier.size(if (manga) MANGA_PICTURE else PICTURE))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    // Manga writes the owner over the title, Classic under it.
                    if (manga) SkeletonLine(type.overline, 0.5f, lineShape, block)
                    SkeletonLine(type.displayEmphasized, 0.85f, lineShape, block)
                    if (!manga) SkeletonLine(type.label, 0.5f, lineShape, block)
                    SkeletonLine(type.caption, 0.6f, lineShape, block)
                }
            }
            SkeletonLine(type.body, 1f, lineShape, block)
            // The platform dock ends the header of every repository that has a release on it.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SkeletonBlock(boxShape, block, Modifier.width(DEVICE_TILE_MAX).height(dockHeight))
                SkeletonBlock(boxShape, block, Modifier.width(TILE_MAX).height(dockHeight))
            }
        }
    }
}

// The version filter, the asset and version pickers and the install button: the rows `header` shows
// once the latest release is on screen, and the same rows while it is being read.
@Composable
internal fun DetailsAssetSkeleton(modifier: Modifier = Modifier) {
    val personality = LocalPersonality.current
    val type = personality.type
    val chipHeight = if (personality is MangaPersonality) MANGA_CHIP else CHIP
    val boxShape = RoundedCornerShape(personality.shape.corner)
    val lineShape = RoundedCornerShape(personality.shape.cornerSmall)
    val block = skeletonBlockColor()
    // Both pickers pad their row by 10dp and keep it at least 36dp tall around the text.
    val assetsRow = maxOf(PICKER_ROW_MIN, lineHeightOf(type.label)) + PICKER_ROW_PADDING * 2
    // The version picker also shows the release name under the version: two text lines.
    val versionRow =
        maxOf(PICKER_ROW_MIN, lineHeightOf(type.label) + lineHeightOf(type.body)) + PICKER_ROW_PADDING * 2

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SkeletonBlock(boxShape, block, Modifier.width(64.dp).height(chipHeight))
            SkeletonBlock(boxShape, block, Modifier.width(96.dp).height(chipHeight))
            SkeletonBlock(boxShape, block, Modifier.width(56.dp).height(chipHeight))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonPicker(
                labelHeight = lineHeightOf(type.overline),
                rowHeight = assetsRow,
                lineShape = lineShape,
                rowShape = boxShape,
                block = block,
                modifier = Modifier.weight(0.65f),
            )
            SkeletonPicker(
                labelHeight = lineHeightOf(type.overline),
                rowHeight = versionRow,
                lineShape = lineShape,
                rowShape = boxShape,
                block = block,
                modifier = Modifier.weight(0.35f),
            )
        }
        SkeletonBlock(boxShape, block, Modifier.fillMaxWidth().height(INSTALL_ROW))
    }
}

@Composable
private fun SkeletonPicker(
    labelHeight: Dp,
    rowHeight: Dp,
    lineShape: Shape,
    rowShape: Shape,
    block: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SkeletonBlock(lineShape, block, Modifier.width(44.dp).height(labelHeight))
        SkeletonBlock(rowShape, block, Modifier.fillMaxWidth().height(rowHeight))
    }
}

@Composable
internal fun SkeletonBlock(
    shape: Shape,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clip(shape).background(color))
}

@Composable
internal fun SkeletonLine(
    style: TextStyle,
    widthFraction: Float,
    shape: Shape,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth(widthFraction)
                .height(lineHeightOf(style))
                .clip(shape)
                .background(color),
    )
}

// The text a block stands in for sets its own line height, so the block follows the personality and
// the system font scale instead of guessing a height.
@Composable
internal fun lineHeightOf(style: TextStyle): Dp {
    val density = LocalDensity.current
    val line = if (style.lineHeight.isSp) style.lineHeight else style.fontSize
    return with(density) { line.toDp() }
}

@Composable
internal fun skeletonBlockColor(): Color {
    val colors = LocalPersonality.current.colors
    return colors.surfaceContainerHigh.copy(alpha = skeletonAlpha())
}

@Composable
private fun skeletonAlpha(): Float {
    val motion = LocalPersonality.current.motion
    if (!motion.animates) return STILL_ALPHA
    val transition = rememberInfiniteTransition(label = "details-skeleton")
    val alpha by
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec =
                infiniteRepeatable(
                    animation = motion.ambient<Float>(),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "details-skeleton-alpha",
        )
    return alpha
}

@Preview
@Composable
private fun DetailsHeaderSkeletonPreview() {
    PersonalityPreview {
        DetailsHeaderSkeleton()
    }
}

@Preview
@Composable
private fun DetailsAssetSkeletonPreview() {
    PersonalityPreview {
        DetailsAssetSkeleton()
    }
}

private val PICTURE = 80.dp
private val MANGA_PICTURE = 84.dp
private val CHIP = 32.dp
private val MANGA_CHIP = 34.dp
private val PICKER_ROW_MIN = 36.dp
private val PICKER_ROW_PADDING = 10.dp
private val INSTALL_ROW = 56.dp
private val DOCK_ICON = 20.dp
private val DEVICE_TILE_MAX = 120.dp
private val TILE_MAX = 96.dp
private const val STILL_ALPHA = 0.4f

package zed.rainxch.details.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.locals.LocalPersonality

@Suppress("FunctionNaming", "MagicNumber", "LongMethod")
@Composable
fun DetailsSkeleton(modifier: Modifier = Modifier) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val transition = rememberInfiniteTransition(label = "detail-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "detail-skeleton-alpha",
    )
    val block = colors.surfaceContainerHigh.copy(alpha = alpha)

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(shape.corner))
                    .background(block),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(block))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .width(180.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(block),
                )
                Box(
                    Modifier
                        .width(110.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(block),
                )
            }
        }
        repeat(3) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(shape.cornerSmall))
                        .background(block),
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth(0.55f)
                .height(16.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(block),
        )
    }
}

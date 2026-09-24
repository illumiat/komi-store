package zed.rainxch.core.presentation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The avatar the profile card draws, shared so the startup warm-up decodes exactly the same
 * size the card requests.
 *
 * Coil keys its memory cache on the resolved size, so a warm-up request at any other size
 * misses it and decodes from disk — the flicker the warm-up exists to remove. Keeping one
 * value here makes the two places that need it fail together, at compile time, instead of
 * drifting silently when only one is changed.
 */
object ProfileAvatarSpec {
    val Size: Dp = 80.dp
}

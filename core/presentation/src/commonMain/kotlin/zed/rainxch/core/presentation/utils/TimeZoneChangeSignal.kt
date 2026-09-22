package zed.rainxch.core.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide signal that the device's system time zone changed.
 *
 * [formatIsoDate], [formatEpochDate] and [formatAddedAt] resolve an instant to a
 * device-local date via `TimeZone.currentSystemDefault()`. That default is not an
 * observable source, so a date already written into a UI model keeps the old
 * zone's value until its producer runs again for some unrelated reason. A producer
 * that displays such a date can observe [revision] and re-run when it changes.
 *
 * The platform layer moves [revision] from its own event source, installed by
 * [ObserveTimeZoneChanges]. Desktop has no equivalent event source, so there the
 * revision never moves on its own.
 */
object TimeZoneChangeSignal {
    private val _revision = MutableStateFlow(0)

    /** Incremented by the platform observer on every time-zone change; starts at 0. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    // Bumped by the platform observer only.
    internal fun onTimeZoneChanged() {
        _revision.update { it + 1 }
    }
}

/**
 * Subscribes to the platform's system-time-zone-changed event for as long as this
 * composable stays in composition, forwarding every event to [TimeZoneChangeSignal].
 *
 * Android listens to `Intent.ACTION_TIMEZONE_CHANGED`; desktop has no equivalent and
 * does nothing. Compose this once near the root of the app.
 */
@Composable
expect fun ObserveTimeZoneChanges()

/**
 * [TimeZoneChangeSignal.revision] observed as Compose state. Reading it inside a
 * composable recomposes that composable whenever the system time zone changes, so a
 * date resolved from `TimeZone.currentSystemDefault()` during that recomposition
 * reflects the new zone instead of a stale frame.
 */
@Composable
fun rememberTimeZoneRevision(): Int {
    val revision by TimeZoneChangeSignal.revision.collectAsState()
    return revision
}

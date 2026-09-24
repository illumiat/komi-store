package zed.rainxch.core.presentation.utils

import androidx.compose.runtime.Composable

/**
 * Desktop has no OS-level time-zone-changed event comparable to Android's
 * `ACTION_TIMEZONE_CHANGED` broadcast, and the JVM caches its default zone, so this
 * is a no-op: on desktop [TimeZoneChangeSignal.revision] stays at 0 and a screen that
 * displays a device-local date only refreshes it on restart or on any other
 * recomposition.
 */
@Composable
actual fun ObserveTimeZoneChanges() = Unit

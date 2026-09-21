package zed.rainxch.githubstore.utils

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

suspend fun <T> readStartupPreference(
    label: String,
    timeout: Duration,
    flow: Flow<T>,
): T? =
    // Startup paths must never hang or crash on a wedged DataStore — they
    // need a value now, so timeouts (handled by withTimeoutOrNull) and any
    // Exception degrade to null. Errors are logged; silent degradation would
    // hide real breakage. Non-Exception Throwables (e.g. OutOfMemoryError)
    // are deliberately NOT caught — they propagate rather than be masked.
    try {
        withTimeoutOrNull(timeout) { flow.first() }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.w(e) { "Startup preference '$label' failed, degrading to null" }
        null
    }

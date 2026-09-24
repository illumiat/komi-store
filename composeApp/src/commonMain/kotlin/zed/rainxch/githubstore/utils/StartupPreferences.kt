package zed.rainxch.githubstore.utils

/**
 * Cold-start bound (ms) for reading persisted preferences. Used by the
 * appearance gate watchdog in MainViewModel, which owns the single startup
 * preference read: if the first emission does not arrive within this window the
 * gate is released on defaults. 2000ms.
 */
internal const val STARTUP_PREFERENCE_TIMEOUT_MS: Long = 2000L

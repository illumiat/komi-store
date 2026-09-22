package zed.rainxch.core.domain.model.account

/**
 * The most recent session this process has observed: whether a token exists, and the
 * account if one has been read.
 *
 * Screens that must render correctly on their very first frame — before any flow has had
 * a chance to emit — read this instead of showing a placeholder and correcting later.
 */
data class SessionSnapshot(
    val isLoggedIn: Boolean,
    val profile: UserProfile?,
)

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
) {
    init {
        // (isLoggedIn = false, profile = nonNull) is not a state any producer creates, but the
        // type allowed it and every consumer silently assumed it away. Reject it here so the
        // impossible pair cannot be constructed and quietly read as "signed out" while still
        // carrying an account.
        require(isLoggedIn || profile == null) {
            // Unconditional read of `profile`: safe only because this message is built when the
            // check failed, and that is exactly the branch where `profile` is non-null. Written
            // as a local so the compiler agrees — `require`'s contract covers the code after it,
            // not inside this lambda, so `profile.username` here would not compile.
            val carried = profile?.username
            "A signed-out snapshot cannot carry a profile: isLoggedIn=$isLoggedIn, " +
                "profile=$carried"
        }
    }
}

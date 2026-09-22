package zed.rainxch.profile.presentation

import zed.rainxch.core.domain.model.account.UserProfile

/**
 * The single description of who the profile screen is showing.
 *
 * The screen previously kept "is the user logged in" and "which account do we have" as two
 * independent fields, so they could disagree: a valid token with no account read yet left
 * the signed-in flag true next to a null profile, and the "Sign in" card rendered above the
 * stars/logout rows. Both facts now live in this one value, so that pair cannot be
 * represented.
 */
sealed interface ProfileSession {
    /**
     * A token exists but no account has been read yet. Covers the cold-start window and a
     * read that produced no account; the token flow resolves it to [SignedOut] and
     * [UserProfile] arrival resolves it to [SignedIn].
     */
    data object Loading : ProfileSession

    /** No account to show, and none on the way. */
    data object SignedOut : ProfileSession

    /** The account is known. [profile] is never null here. */
    data class SignedIn(val profile: UserProfile) : ProfileSession
}

data class ProfileState(
    val session: ProfileSession = ProfileSession.SignedOut,
    val isLogoutDialogVisible: Boolean = false,
) {
    /** The one predicate every signed-in/signed-out branch reads. */
    val isUserLoggedIn: Boolean get() = session is ProfileSession.SignedIn

    /** The account behind [session], for the content that renders it. */
    val userProfile: UserProfile? get() = (session as? ProfileSession.SignedIn)?.profile
}

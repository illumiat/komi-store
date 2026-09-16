package zed.rainxch.profile.presentation

import zed.rainxch.core.domain.model.account.UserProfile

data class ProfileState(
    val userProfile: UserProfile? = null,
    val isLogoutDialogVisible: Boolean = false,
    val isUserLoggedIn: Boolean = false,
    // False until the session has been read once. Without it a null userProfile is
    // indistinguishable from "signed out", so the first frame renders the signed-out
    // card and then swaps to the real account.
    val isSessionResolved: Boolean = false,
)

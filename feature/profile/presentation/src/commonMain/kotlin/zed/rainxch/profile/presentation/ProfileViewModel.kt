package zed.rainxch.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.model.account.SessionSnapshot
import zed.rainxch.core.domain.repository.UserSessionRepository

class ProfileViewModel(
    private val userSessionRepository: UserSessionRepository
) : ViewModel() {
    private var userProfileJob: Job? = null

    // Seeded from the session the process already knows about, so the first frame shows
    // the real card rather than a placeholder the user then watches change. On a cold
    // start that snapshot is filled during startup, behind the splash.
    private val _state =
        MutableStateFlow(
            ProfileState(session = sessionFrom(userSessionRepository.lastKnownSession)),
        )

    // Exposed directly rather than through stateIn(WhileSubscribed): _state is driven by
    // viewModelScope for the whole lifetime of the ViewModel, so a shared coroutine added
    // nothing but a stale replay — after the last collector left, the next one was handed
    // the retained value first and only then corrected, flashing the old session (the
    // signed-in card after a 401 sign-out) for a frame.
    val state = _state.asStateFlow()

    private val _events = Channel<ProfileEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        observeLoggedInStatus()
    }

    /** The only way [ProfileState.session] changes after construction. */
    private fun setSession(session: ProfileSession) {
        _state.update { it.copy(session = session) }
    }

    private fun observeLoggedInStatus() {
        viewModelScope.launch {
            userSessionRepository.isUserLoggedIn()
                .collect { isLoggedIn ->
                    if (isLoggedIn) {
                        // Deliberately not resolving to SignedIn here. A token read
                        // finishes long before the account does, and claiming an account
                        // we have not read would show the "Sign in" card next to the
                        // signed-in rows. Loading keeps the whole screen consistent;
                        // loadUserProfile is what resolves the account.
                        if (_state.value.session !is ProfileSession.SignedIn) {
                            setSession(ProfileSession.Loading)
                        }
                        loadUserProfile()
                    } else {
                        // Cancel the in-flight profile read first. It can still emit after
                        // this point (a network round-trip started while the token was
                        // present), and its setSession(SignedIn) would otherwise refill the
                        // state after we have decided the session is over — and its
                        // recordSession(true, profile) would write the expired account back
                        // into lastKnownSession.
                        userProfileJob?.cancel()
                        // Not going through getUser() on this path, so the snapshot has to
                        // be cleared here: otherwise a token that disappeared without a
                        // logout would leave a stale account to seed the next instance.
                        userSessionRepository.clearLastKnownSession()
                        setSession(ProfileSession.SignedOut)
                    }
                }
        }
    }

    private fun loadUserProfile() {
        userProfileJob?.cancel()

        userProfileJob = viewModelScope.launch {
            userSessionRepository.getUser().collect { profile ->
                // A null here means "no account", which is already represented by
                // Loading/SignedOut; the token flow owns the signed-out transition, so
                // only an account resolves this. Keeping the previously shown account
                // rather than reacting to null is what stops the card from flipping.
                if (profile != null) {
                    setSession(ProfileSession.SignedIn(profile))
                }
            }
        }
    }

    fun onAction(action: ProfileAction) {
        when (action) {
            ProfileAction.OnLogoutClick -> {
                _state.update {
                    it.copy(
                        isLogoutDialogVisible = true,
                    )
                }
            }

            ProfileAction.OnLogoutConfirmClick -> {
                viewModelScope.launch {
                    runCatching {
                        userSessionRepository.logout()
                    }.onSuccess {
                        _state.update { it.copy(isLogoutDialogVisible = false) }
                        setSession(ProfileSession.SignedOut)
                        _events.send(ProfileEvent.OnLogoutSuccessful)
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        _state.update { it.copy(isLogoutDialogVisible = false) }
                        error.message?.let {
                            _events.send(ProfileEvent.OnLogoutError(it))
                        }
                    }
                }
            }

            ProfileAction.OnLogoutDismiss -> {
                _state.update {
                    it.copy(
                        isLogoutDialogVisible = false,
                    )
                }
            }

            ProfileAction.OnLoginClick,
            ProfileAction.OnFavouriteReposClick,
            ProfileAction.OnStarredReposClick,
            is ProfileAction.OnRepositoriesClick,
            ProfileAction.OnRecentlyViewedClick,
            ProfileAction.OnWhatsNewClick,
            ProfileAction.OnAnnouncementsClick,
            ProfileAction.OnTweaksClick,
            ProfileAction.OnAboutClick -> Unit
        }
    }
}

// Maps the process-wide snapshot to the screen's single session value. A snapshot that says
// "token, account not read yet" seeds Loading rather than a signed-in flag next to a null
// account, which is the pair that used to misplace the "Sign in" card.
private fun sessionFrom(snapshot: SessionSnapshot?): ProfileSession {
    if (snapshot == null || !snapshot.isLoggedIn) return ProfileSession.SignedOut
    // Read through a local: `SessionSnapshot.profile` is declared in another module, so the
    // null check below cannot smart-cast the property itself.
    val profile = snapshot.profile
    return if (profile != null) ProfileSession.SignedIn(profile) else ProfileSession.Loading
}

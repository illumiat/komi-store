package zed.rainxch.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.repository.UserSessionRepository

class ProfileViewModel(
    private val userSessionRepository: UserSessionRepository
) : ViewModel() {
    private var userProfileJob: Job? = null

    private var hasLoadedInitialData = false

    // Seeded from the session the process already knows about, so the first frame shows
    // the real card rather than a placeholder the user then watches change. On a cold
    // start that snapshot is filled during startup, behind the splash.
    private val _state =
        MutableStateFlow(
            userSessionRepository.lastKnownSession?.let { session ->
                ProfileState(
                    userProfile = session.profile,
                    // The card renders signed-out whenever userProfile is null, so seeding
                    // isUserLoggedIn = true next to a null profile put the "Sign in" card
                    // above the stars/logout rows for a frame. (true, null) is a real
                    // snapshot — valid token, account not read yet — and the flow below
                    // re-states the flag a frame later.
                    isUserLoggedIn = session.isLoggedIn && session.profile != null,
                )
            } ?: ProfileState(),
        )
    val state = _state
        .onStart {
            if (!hasLoadedInitialData) {
                observeLoggedInStatus()

                hasLoadedInitialData = true
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = _state.value,
        )

    private val _events = Channel<ProfileEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private fun observeLoggedInStatus() {
        viewModelScope.launch {
            userSessionRepository.isUserLoggedIn()
                .collect { isLoggedIn ->
                    if (isLoggedIn) {
                        // Deliberately not resolving here. A token read finishes long
                        // before the account does, and resolving now would render the
                        // signed-out card in the gap. loadUserProfile resolves instead.
                        _state.update { it.copy(isUserLoggedIn = true) }
                        loadUserProfile()
                    } else {
                        // Not going through getUser() on this path, so the snapshot has to
                        // be cleared here: otherwise a token that disappeared without a
                        // logout would leave a stale account to seed the next instance.
                        userSessionRepository.clearLastKnownSession()
                        _state.update {
                            it.copy(isUserLoggedIn = false, userProfile = null)
                        }
                    }
                }
        }
    }

    private fun loadUserProfile() {
        userProfileJob?.cancel()

        userProfileJob = viewModelScope.launch {
            userSessionRepository.getUser().collect { profile ->
                _state.update { it.copy(userProfile = profile) }
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
                        _state.update { it.copy(isLogoutDialogVisible = false, userProfile = null) }
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

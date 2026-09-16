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
import zed.rainxch.core.domain.model.account.UserProfile
import zed.rainxch.core.domain.repository.UserSessionRepository

class ProfileViewModel(
    private val userSessionRepository: UserSessionRepository
) : ViewModel() {
    private var userProfileJob: Job? = null

    private var hasLoadedInitialData = false

    // The account is read from the database, so the very first frame of a recreated
    // ViewModel has nothing to show. Seeding from the last known account lets that frame
    // render the real one instead of flashing a placeholder.
    private val _state = MutableStateFlow(ProfileState(userProfile = lastKnownProfile))
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
                        lastKnownProfile = null
                        _state.update {
                            it.copy(
                                isUserLoggedIn = false,
                                userProfile = null,
                                isSessionResolved = true,
                            )
                        }
                    }
                }
        }
    }

    private fun loadUserProfile() {
        userProfileJob?.cancel()

        userProfileJob = viewModelScope.launch {
            userSessionRepository.getUser().collect { profile ->
                lastKnownProfile = profile
                _state.update { it.copy(userProfile = profile, isSessionResolved = true) }
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
                        lastKnownProfile = null
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

    private companion object {
        // Held for the process, not the ViewModel. Switching tabs recreates the
        // ViewModel, and the account itself lives in the database, so a fresh instance
        // has nothing to render on its first frame without this.
        var lastKnownProfile: UserProfile? = null
    }
}

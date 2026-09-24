package zed.rainxch.githubstore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.RateLimitRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase

class MainViewModel(
    private val tweaksRepository: TweaksRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val userSessionRepository: UserSessionRepository,
    private val rateLimitRepository: RateLimitRepository,
    private val syncUseCase: SyncInstalledAppsUseCase,
    private val logger: KomiStoreLogger,
) : ViewModel() {
    private val _state = MutableStateFlow(MainState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Read the stored session once, at startup, so a screen that must be correct on
            // its very first frame — the profile tab — already has the account before anyone
            // can open it. Local only, so it costs a database read, and nothing waits on it.
            try {
                userSessionRepository.primeSession()
            } catch (e: CancellationException) {
                // Not a failure: swallowing this would let the cancelled coroutine run on
                // instead of ending with it.
                throw e
            } catch (e: Exception) {
                logger.warn("Session prime failed; continuing without it: ${e.message}")
            }
            _state.update {
                it.copy(
                    signedInAvatarUrl = userSessionRepository.lastKnownSession?.profile?.imageUrl,
                )
            }
        }

        // Deliberately its own launch, not the one above: the login state must be observed
        // even if the prime read never returns (a stalled store, a slow disk). Sharing a
        // launch made the collector wait behind it, so a stuck prime meant a session that was
        // never observed and rateLimitRepository.clear() that never fired on sign-in.
        viewModelScope.launch(Dispatchers.IO) {
            userSessionRepository
                .isUserLoggedIn()
                .collect { isLoggedIn ->
                    // The warm-up target belongs to whoever is signed in *now*: on a sign-out
                    // it must go, or the next account's tab would warm the previous account's
                    // avatar. The snapshot's own flag is checked as well, because between
                    // accounts it still holds the previous account's profile until the new
                    // one is read.
                    var avatarUrl =
                        if (isLoggedIn) {
                            userSessionRepository.lastKnownSession
                                ?.takeIf { it.isLoggedIn }
                                ?.profile
                                ?.imageUrl
                        } else {
                            null
                        }

                    if (isLoggedIn && avatarUrl == null) {
                        // Signed in with no account read yet — a login in this process, or a
                        // cold start with an empty profile cache. Nothing would warm, because
                        // recordSession (which fills the profile) does not emit on the token
                        // flow. getUser() records it, so read it once here.
                        avatarUrl = userSessionRepository.getUser().first()?.imageUrl
                    }

                    _state.update {
                        it.copy(
                            isLoggedIn = isLoggedIn,
                            signedInAvatarUrl = avatarUrl,
                        )
                    }

                    if (isLoggedIn) {
                        rateLimitRepository.clear()
                    }
                }
        }

        viewModelScope.launch {
            tweaksRepository
                .getThemeColor()
                .collect { theme ->
                    _state.update {
                        it.copy(currentColorTheme = theme)
                    }
                }
        }
        viewModelScope.launch {
            tweaksRepository
                .getAmoledTheme()
                .collect { isAmoled ->
                    _state.update {
                        it.copy(isAmoledTheme = isAmoled)
                    }
                }
        }
        viewModelScope.launch {
            tweaksRepository
                .getIsDarkTheme()
                .collect { isDarkTheme ->
                    _state.update {
                        it.copy(isDarkTheme = isDarkTheme)
                    }
                }
        }

        viewModelScope.launch {
            tweaksRepository
                .getFontTheme()
                .collect { fontTheme ->
                    _state.update {
                        it.copy(currentFontTheme = fontTheme)
                    }
                }
        }

        viewModelScope.launch {
            tweaksRepository.getPersonality().collect { personality ->
                _state.update { it.copy(personality = personality) }
            }
        }

        viewModelScope.launch {
            tweaksRepository.getAccentId().collect { accent ->
                _state.update { it.copy(accent = accent) }
            }
        }

        viewModelScope.launch {
            tweaksRepository.getMangaPaper().collect { paper ->
                _state.update { it.copy(mangaPaper = paper) }
            }
        }

        viewModelScope.launch {
            tweaksRepository.getScrollbarEnabled().collect { enabled ->
                _state.update { it.copy(isScrollbarEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            tweaksRepository.getContentWidth().collect { width ->
                _state.update { it.copy(contentWidth = width) }
            }
        }

        viewModelScope.launch {
            tweaksRepository.getAppLanguage().collect { tag ->
                _state.update { it.copy(appLanguageTag = tag) }
            }
        }

        viewModelScope.launch {
            rateLimitRepository.rateLimitState.collect { rateLimitInfo ->
                _state.update { currentState ->
                    currentState.copy(rateLimitInfo = rateLimitInfo)
                }
            }
        }

        viewModelScope.launch {
            rateLimitRepository.rateLimitExhaustedEvent.collect { info ->
                _state.update { it.copy(showRateLimitDialog = true, rateLimitInfo = info) }
            }
        }

        viewModelScope.launch {
            userSessionRepository.sessionExpiredEvent.collect {
                // The token is gone by the time this fires, so drop the warm-up target with
                // it rather than waiting for the token flow to report the sign-out.
                _state.update {
                    it.copy(showSessionExpiredDialog = true, signedInAvatarUrl = null)
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            syncUseCase().onSuccess {
                installedAppsRepository.checkAllForUpdates()
            }
        }
    }

    fun onAction(action: MainAction) {
        when (action) {
            MainAction.DismissRateLimitDialog -> {
                _state.update { it.copy(showRateLimitDialog = false) }
            }

            MainAction.DismissSessionExpiredDialog -> {
                _state.update { it.copy(showSessionExpiredDialog = false) }
            }
        }
    }
}

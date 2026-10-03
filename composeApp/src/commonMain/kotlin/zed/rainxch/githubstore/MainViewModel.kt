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
            try {
                userSessionRepository.primeSession()
            } catch (e: CancellationException) {
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

        viewModelScope.launch(Dispatchers.IO) {
            userSessionRepository
                .isUserLoggedIn()
                .collect { isLoggedIn ->
                    val avatarUrl =
                        if (isLoggedIn) {
                            userSessionRepository.lastKnownSession
                                ?.takeIf { it.isLoggedIn }
                                ?.profile
                                ?.imageUrl
                        } else {
                            null
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

                    if (isLoggedIn && avatarUrl == null) {
                        launch {
                            val fetched = userSessionRepository.getUser().first()?.imageUrl
                            if (fetched != null &&
                                _state.value.isLoggedIn &&
                                userSessionRepository.lastKnownSession?.isLoggedIn == true
                            ) {
                                _state.update { it.copy(signedInAvatarUrl = fetched) }
                            }
                        }
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

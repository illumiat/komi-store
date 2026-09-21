package zed.rainxch.githubstore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.domain.model.appearance.MangaPaperId
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.RateLimitRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase
import zed.rainxch.githubstore.utils.STARTUP_PREFERENCE_TIMEOUT_MS
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(
    private val tweaksRepository: TweaksRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val userSessionRepository: UserSessionRepository,
    private val rateLimitRepository: RateLimitRepository,
    private val syncUseCase: SyncInstalledAppsUseCase,
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
                // Not a failure: swallowing this would let the collector below run on a
                // cancelled coroutine instead of ending with it.
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Session prime failed; continuing without it" }
            }
            _state.update {
                it.copy(
                    signedInAvatarUrl = userSessionRepository.lastKnownSession?.profile?.imageUrl,
                )
            }

            userSessionRepository
                .isUserLoggedIn()
                .collect { isLoggedIn ->
                    _state.update {
                        it.copy(
                            isLoggedIn = isLoggedIn,
                            // The warm-up target belongs to whoever is signed in *now*: on a
                            // sign-out it must go, or the next account's tab would warm the
                            // previous account's avatar.
                            signedInAvatarUrl =
                                if (isLoggedIn) {
                                    userSessionRepository.lastKnownSession?.profile?.imageUrl
                                } else {
                                    null
                                },
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
                .getFontTheme()
                .collect { fontTheme ->
                    _state.update {
                        it.copy(currentFontTheme = fontTheme)
                    }
                }
        }

        // Sole writer of the gated appearance fields: combine emits only after
        // all five sources have a first value, so fields and flag land in one
        // update. If that emission never arrives within the timeout — or the
        // collector throws — the watchdog releases the gate on defaults so
        // the splash can never be held indefinitely.
        viewModelScope.launch {
            val firstEmitted = CompletableDeferred<Unit>()
            launch {
                if (
                    withTimeoutOrNull(STARTUP_PREFERENCE_TIMEOUT_MS.milliseconds) {
                        firstEmitted.await()
                    } == null
                ) {
                    Logger.w { "Appearance preference load timed out, releasing gate on defaults" }
                    _state.update { it.copy(isAppearanceLoaded = true) }
                }
            }
            try {
                combine(
                    tweaksRepository.getPersonality(),
                    tweaksRepository.getAccentId(),
                    tweaksRepository.getMangaPaper(),
                    tweaksRepository.getAmoledTheme(),
                    tweaksRepository.getIsDarkTheme(),
                ) { personality, accent, paper, amoled, isDark ->
                    Appearance(personality, accent, paper, amoled, isDark)
                }.combine(tweaksRepository.getAppLanguage()) { appearance, appLanguageTag ->
                    appearance.copy(appLanguageTag = appLanguageTag)
                }.collect { snapshot ->
                    _state.update { it.withAppearance(snapshot).copy(isAppearanceLoaded = true) }
                    firstEmitted.complete(Unit)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Appearance preference stream failed, releasing gate on defaults" }
                _state.update { it.copy(isAppearanceLoaded = true) }
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

private data class Appearance(
    val personality: AppPersonality,
    val accent: AccentId,
    val mangaPaper: MangaPaperId,
    val isAmoledTheme: Boolean,
    val isDarkTheme: Boolean?,
    val appLanguageTag: String? = null,
)

private fun MainState.withAppearance(snapshot: Appearance): MainState =
    copy(
        personality = snapshot.personality,
        accent = snapshot.accent,
        mangaPaper = snapshot.mangaPaper,
        isAmoledTheme = snapshot.isAmoledTheme,
        isDarkTheme = snapshot.isDarkTheme,
        appLanguageTag = snapshot.appLanguageTag,
    )

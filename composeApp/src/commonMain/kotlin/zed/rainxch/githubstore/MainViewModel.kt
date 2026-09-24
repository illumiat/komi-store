package zed.rainxch.githubstore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import zed.rainxch.core.data.services.LocalizationManager
import zed.rainxch.core.domain.logging.KomiStoreLogger
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
    private val localizationManager: LocalizationManager,
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

        // Sole writer of the gated appearance fields: the combine chain emits
        // only after all six sources have a first value — the five theme
        // preferences plus appLanguageTag, which selects the font script and is
        // therefore gated alongside the theme rather than collected separately.
        // Fields and flag land in one update. If that emission never arrives
        // within the timeout — or the collector throws — the watchdog releases
        // the gate on defaults so the splash can never be held indefinitely.
        viewModelScope.launch {
            val firstEmitted = CompletableDeferred<Unit>()
            launch {
                if (
                    withTimeoutOrNull(STARTUP_PREFERENCE_TIMEOUT_MS.milliseconds) {
                        firstEmitted.await()
                    } == null
                ) {
                    // Timed out before the language was read, so no locale is applied: the
                    // system default is the correct fallback and the gate opens on defaults.
                    logger.warn("Appearance preference load timed out, releasing gate on defaults")
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
                    // The one place the startup language is read. The platform entry points used to read
                    // it again on their own timeout budget, so a slow first read could leave the JVM
                    // locale and the rendered language disagreeing. Applied before the gate opens — the
                    // splash still covers this window — so the first real frame already resolves its
                    // resources against the stored language.
                    localizationManager.setActiveLanguageTag(snapshot.appLanguageTag)
                    _state.update { it.withAppearance(snapshot).copy(isAppearanceLoaded = true) }
                    firstEmitted.complete(Unit)
                }
            } catch (e: CancellationException) {
                // Cancellation, not a degraded release: no gate update and no locale is applied.
                throw e
            } catch (e: Exception) {
                // The stream failed before any language arrived, so no locale is applied here —
                // the system default stays and only the gate is released.
                logger.error(
                    "Appearance preference stream failed, releasing gate on defaults",
                    e,
                )
                _state.update { it.copy(isAppearanceLoaded = true) }
                firstEmitted.complete(Unit)
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

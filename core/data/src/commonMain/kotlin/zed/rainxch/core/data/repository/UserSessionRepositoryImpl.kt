package zed.rainxch.core.data.repository

import kotlinx.coroutines.CancellationException
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.USER_PROFILE
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.data.dto.GithubDeviceTokenSuccessDto
import zed.rainxch.core.data.dto.UserProfileNetwork
import zed.rainxch.core.data.mappers.toUserProfile
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.SessionSnapshot
import zed.rainxch.core.domain.model.account.UserProfile
import zed.rainxch.core.domain.repository.UserSessionRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class UserSessionRepositoryImpl(
    private val tokenStore: TokenStore,
    private val cacheManager: CacheManager,
    private val httpClientProvider: () -> HttpClient,
    private val logger: KomiStoreLogger
) : UserSessionRepository {
    private val httpClient: HttpClient get() = httpClientProvider()

    private val _sessionExpiredEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val sessionExpiredEvent: SharedFlow<Unit> = _sessionExpiredEvent.asSharedFlow()

    private val sessionExpiredMutex = Mutex()

    private var _failingTokenSnapshot: String? = null
    private var _firstFailureAtMillis: Long = 0L
    private var _consecutiveFailures: Int = 0

    override fun isUserLoggedIn(): Flow<Boolean> =
        tokenStore
            .tokenFlow()
            .map { it != null }

    override suspend fun isCurrentlyUserLoggedIn(): Boolean = tokenStore.currentToken() != null

    @Volatile
    private var _lastKnownSession: SessionSnapshot? = null

    override val lastKnownSession: SessionSnapshot? get() = _lastKnownSession

    private fun recordSession(isLoggedIn: Boolean, profile: UserProfile?) {
        _lastKnownSession = SessionSnapshot(isLoggedIn, profile)
    }

    override fun clearLastKnownSession() {
        recordSession(isLoggedIn = false, profile = null)
    }

    /**
     * The cached profile, but only when it was stored under [token].
     *
     * The entry is keyed by a fixed "profile:me" that outlives the token, and replacing
     * the token (logging in as another account) does not re-key it, so without this stamp
     * check a new account could be seeded from the previous account's cached profile.
     * Stale is acceptable once the stamp matches.
     *
     * A stamp missing on either side means "owner unknown", not "somebody else": caches
     * written before this field existed must stay readable, otherwise upgrading would
     * blank the profile until the first successful fetch. Only two known, different
     * owners reject the cache.
     */
    private suspend fun ownedCachedProfile(token: GithubDeviceTokenSuccessDto): UserProfile? {
        val stored =
            cacheManager.get<String>(CACHE_OWNER_KEY)
                ?: cacheManager.getStale<String>(CACHE_OWNER_KEY)
        val current = ownershipStamp(token)
        if (stored != null && current != null && stored != current) return null
        return cacheManager.get<UserProfile>(CACHE_KEY)
            ?: cacheManager.getStale<UserProfile>(CACHE_KEY)
    }

    // Identifies the token without being derivable from it: the moment the token was
    // stored, which TokenStore guarantees is present and which only changes on sign-in.
    // A token hash was the obvious alternative, but a 32-bit hash can collide, and this
    // check exists precisely to stop one account's profile being shown for another's.
    private fun ownershipStamp(token: GithubDeviceTokenSuccessDto): String? =
        token.savedAtEpochMillis?.toString()

    override suspend fun primeSession() {
        val token = tokenStore.currentToken()
        // Stale is acceptable here: showing yesterday's account beats showing a
        // placeholder, and the normal read replaces it moments later. Never the
        // network — startup must not wait on GitHub.
        val profile = token?.let { ownedCachedProfile(it) }
        recordSession(token != null, profile)
    }

    override fun getUser(): Flow<UserProfile?> = flow {
        val token = tokenStore.currentToken()
        if (token == null) {
            cacheManager.invalidate(CACHE_KEY)
            recordSession(isLoggedIn = false, profile = null)
            emit(null)
            return@flow
        }

        val cached = ownedCachedProfile(token)
        if (cached != null) {
            logger.debug("Profile cache hit")
            recordSession(isLoggedIn = true, profile = cached)
            emit(cached)
            return@flow
        }

        try {
            val networkProfile =
                httpClient
                    .executeRequest<UserProfileNetwork> {
                        get("/user") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                        }
                    }.getOrThrow()

            val userProfile = networkProfile.toUserProfile()
            cacheManager.put(CACHE_KEY, userProfile, USER_PROFILE)
            ownershipStamp(token)?.let { cacheManager.put(CACHE_OWNER_KEY, it, USER_PROFILE) }
            logger.debug("Fetched and cached user profile: ${userProfile.username}")
            recordSession(isLoggedIn = true, profile = userProfile)
            emit(userProfile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to fetch user profile: ${e.message}")

            val stale = ownedCachedProfile(token)
            if (stale != null) {
                logger.debug("Using stale cached profile as fallback")
                recordSession(isLoggedIn = true, profile = stale)
                emit(stale)
            } else {
                recordSession(isLoggedIn = true, profile = null)
                emit(null)
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun notifySessionExpired(tokenKey: String?) {
        if (tokenKey.isNullOrEmpty()) return
        sessionExpiredMutex.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            if (tokenKey != _failingTokenSnapshot ||
                now - _firstFailureAtMillis > FAILURE_WINDOW_MS
            ) {
                _failingTokenSnapshot = tokenKey
                _firstFailureAtMillis = now
                _consecutiveFailures = 1
            } else {
                _consecutiveFailures += 1
            }

            if (_consecutiveFailures < REQUIRED_CONSECUTIVE_FAILURES) {
                Logger.w(TAG) {
                    "notifySessionExpired: 401 count=$_consecutiveFailures (need " +
                        "$REQUIRED_CONSECUTIVE_FAILURES); deferring sign-out"
                }
                return@withLock
            }

            val current = tokenStore.currentToken()?.accessToken
            if (current != tokenKey) {
                Logger.w(TAG) {
                    "notifySessionExpired: stored token rotated since the failing " +
                        "request; skipping clear"
                }
                resetCounter()
                return@withLock
            }

            Logger.w(TAG) {
                "notifySessionExpired: $_consecutiveFailures consecutive 401s within " +
                    "window; clearing token"
            }
            tokenStore.clear()
            // The profile cache outlives the token (6h TTL) and is only wiped on logout, so a
            // later login as a different account could otherwise be primed from the previous
            // account's profile. Drop it together with the token.
            cacheManager.invalidate(CACHE_KEY)
            // Keep the session snapshot honest: the token is gone, so the session is signed
            // out. Without this, _lastKnownSession would still report isLoggedIn = true with
            // the old account after a 401-driven sign-out.
            recordSession(isLoggedIn = false, profile = null)
            resetCounter()
            _sessionExpiredEvent.emit(Unit)
        }
    }

    override suspend fun notifyRequestSucceeded(tokenKey: String?) {
        if (tokenKey.isNullOrEmpty()) return
        sessionExpiredMutex.withLock {
            if (tokenKey == _failingTokenSnapshot) {
                resetCounter()
            }
        }
    }

    private fun resetCounter() {
        _failingTokenSnapshot = null
        _firstFailureAtMillis = 0L
        _consecutiveFailures = 0
    }

    override suspend fun logout() {
        tokenStore.clear()
        cacheManager.clearAll()
        recordSession(isLoggedIn = false, profile = null)
    }

    private companion object {
        const val TAG = "AuthState"
        const val REQUIRED_CONSECUTIVE_FAILURES = 2
        const val FAILURE_WINDOW_MS = 60_000L
        private const val CACHE_KEY = "profile:me"
        // Records which token the CACHE_KEY profile was fetched under, so it is never
        // served to a different account. Deliberately a non-reversible fingerprint rather
        // than the token itself: the cache is not encrypted storage.
        private const val CACHE_OWNER_KEY = "profile:me:owner"
    }
}

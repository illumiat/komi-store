package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import zed.rainxch.core.domain.model.account.SessionSnapshot
import zed.rainxch.core.domain.model.account.UserProfile

interface UserSessionRepository {
    fun isUserLoggedIn(): Flow<Boolean>
    fun getUser(): Flow<UserProfile?>

    suspend fun isCurrentlyUserLoggedIn(): Boolean

    /**
     * The most recent session observed in this process. Never null after the first read:
     * once observed, the live state is always represented by a [SessionSnapshot], and a
     * signed-out state is [SessionSnapshot.isLoggedIn] = false with a null profile rather
     * than a null snapshot. Non-suspend, so a screen can seed its first frame from it.
     */
    val lastKnownSession: SessionSnapshot?

    /**
     * Reads the persisted session — never the network — and records it as
     * [lastKnownSession]. Called once during startup, behind the splash, so the first
     * frame of any screen that needs the account already has it.
     */
    suspend fun primeSession()

    /**
     * Records the logged-out session as the current [lastKnownSession] (isLoggedIn = false,
     * profile = null) so it is never null after the first observation. Called wherever the
     * token is found to be gone, so a signed-out user is never seeded from an account that
     * is no longer theirs. Note this writes a logged-out snapshot rather than clearing the
     * field to null; callers must not rely on [lastKnownSession] being null to mean
     * "unknown".
     */
    fun clearLastKnownSession()

    val sessionExpiredEvent: SharedFlow<Unit>

    suspend fun notifySessionExpired(tokenKey: String?)

    suspend fun notifyRequestSucceeded(tokenKey: String?)
    suspend fun logout()
}

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
     * The most recent session observed in this process, or null if none has been read yet.
     * Non-suspend, so a screen can seed its first frame from it.
     */
    val lastKnownSession: SessionSnapshot?

    /**
     * Reads the persisted session — never the network — and records it as
     * [lastKnownSession]. Called once during startup, behind the splash, so the first
     * frame of any screen that needs the account already has it.
     */
    suspend fun primeSession()

    /**
     * Forgets [lastKnownSession]. Called wherever the token is found to be gone, so a
     * signed-out user is never seeded from an account that is no longer theirs.
     */
    fun clearLastKnownSession()

    val sessionExpiredEvent: SharedFlow<Unit>

    suspend fun notifySessionExpired(tokenKey: String?)

    suspend fun notifyRequestSucceeded(tokenKey: String?)
    suspend fun logout()
}

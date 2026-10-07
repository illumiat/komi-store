package zed.rainxch.core.data.services.installer

/**
 * Pure decisions for reclaiming orphaned PackageInstaller sessions.
 *
 * A session created via PackageInstaller.createSession survives process death: if the process
 * dies between createSession and commit/abandon, the system keeps the session around and nobody
 * ever reclaims it. On the next install we sweep the sessions this app owns, keeping only the
 * ones the current process is actively driving.
 */
internal object OrphanSessionReclaim {

    /** Sessions present system-side minus the ones this process is currently driving. */
    fun orphanedSessionIds(allSessionIds: List<Int>, activeSessionIds: Set<Int>): List<Int> =
        allSessionIds.filterNot { it in activeSessionIds }

    /**
     * Abandons each id. A throwing [abandon] is recorded and must not stop the remaining ids;
     * returns the ids that failed so the caller can log them.
     */
    fun abandonAll(sessionIds: List<Int>, abandon: (Int) -> Unit): List<Int> =
        sessionIds.filter { id -> runCatching { abandon(id) }.isFailure }
}

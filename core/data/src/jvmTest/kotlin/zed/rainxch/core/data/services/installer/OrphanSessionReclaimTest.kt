package zed.rainxch.core.data.services.installer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrphanSessionReclaimTest {

    @Test
    fun no_sessions_means_nothing_to_abandon() {
        assertEquals(emptyList(), OrphanSessionReclaim.orphanedSessionIds(emptyList(), emptySet()))
    }

    @Test
    fun when_every_session_is_active_there_is_no_orphan() {
        assertEquals(
            emptyList(),
            OrphanSessionReclaim.orphanedSessionIds(listOf(1, 2, 3), setOf(1, 2, 3)),
        )
    }

    @Test
    fun every_session_outside_the_active_set_is_collected() {
        assertEquals(
            listOf(2, 3),
            OrphanSessionReclaim.orphanedSessionIds(listOf(1, 2, 3), setOf(1)),
        )
    }

    @Test
    fun the_active_session_is_never_abandoned() {
        val active = setOf(42)
        val orphans = OrphanSessionReclaim.orphanedSessionIds(listOf(7, 42, 99), active)
        assertTrue(42 !in orphans, "the in-flight session must survive the sweep")
        assertEquals(listOf(7, 99), orphans)
    }

    @Test
    fun a_failing_abandon_does_not_stop_the_rest() {
        val attempted = mutableListOf<Int>()
        val failed =
            OrphanSessionReclaim.abandonAll(listOf(1, 2, 3)) { id ->
                attempted += id
                if (id == 2) error("abandon $id failed")
            }

        assertEquals(listOf(1, 2, 3), attempted, "every id is attempted despite the failure")
        assertEquals(listOf(2), failed, "only the throwing id is reported as failed")
    }
}

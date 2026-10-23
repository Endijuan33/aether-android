package id.mybase.aether.persistence

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryStoreTest {

    @Test
    fun `record returns incrementing ids starting at 1`() = runTest {
        val store = InMemoryOperationHistoryStore()
        assertEquals(1L, store.record(100L, "p1", "l1", "DISABLE", "SUCCESS", "ok"))
        assertEquals(2L, store.record(200L, "p2", "l2", "ENABLE", "FAILED", "nope"))
    }

    @Test
    fun `record stores every field`() = runTest {
        val store = InMemoryOperationHistoryStore()
        store.record(12345L, "com.x.y", "X Y", "UNINSTALL_FOR_USER", "SKIPPED", "invalid id")
        val row = store.list().single()
        assertEquals(12345L, row.timestamp)
        assertEquals("com.x.y", row.packageName)
        assertEquals("X Y", row.label)
        assertEquals("UNINSTALL_FOR_USER", row.operation)
        assertEquals("SKIPPED", row.status)
        assertEquals("invalid id", row.message)
    }

    @Test
    fun `list returns newest first and honors limit`() = runTest {
        val store = InMemoryOperationHistoryStore()
        repeat(5) { i ->
            store.record(i.toLong(), "p$i", "l$i", "DISABLE", "SUCCESS", "m$i")
        }
        val all = store.list()
        assertEquals(5, all.size)
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), all.map { it.id })

        val limited = store.list(3)
        assertEquals(3, limited.size)
        assertEquals(listOf(5L, 4L, 3L), limited.map { it.id })

        assertTrue(store.list(0).isEmpty())
    }

    @Test
    fun `clear empties the store but keeps id sequence`() = runTest {
        val store = InMemoryOperationHistoryStore()
        store.record(1L, "p", "l", "DISABLE", "SUCCESS", "m")
        store.clear()
        assertTrue(store.list().isEmpty())
        // Ids keep incrementing after clear (append-mostly log semantics).
        assertEquals(2L, store.record(2L, "p", "l", "DISABLE", "SUCCESS", "m"))
    }

    @Test
    fun `default limit is 200`() = runTest {
        val store = InMemoryOperationHistoryStore()
        repeat(250) { i ->
            store.record(i.toLong(), "p$i", "l$i", "DISABLE", "SUCCESS", "m")
        }
        assertEquals(200, store.list().size)
    }
}

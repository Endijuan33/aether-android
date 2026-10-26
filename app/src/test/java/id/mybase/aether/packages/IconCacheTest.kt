package id.mybase.aether.packages

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class IconCacheTest {
    @Test
    fun cachesSuccessAndMissingLookupsAndEvictsLeastRecentlyUsed() = runTest {
        val cache = IconCache<String, String>(2)
        var calls = 0
        suspend fun load(key: String): String? =
            cache.get(key) {
                calls++
                if (key == "missing") null else key
            }
        assertEquals("user", load("user"))
        assertNull(load("missing"))
        assertNull(load("missing"))
        assertEquals("user", load("user"))
        assertEquals(2, calls)
        load("system")
        load("user")
        assertEquals(3, calls)
        load("missing")
        assertEquals(4, calls)
        cache.clear()
        load("user")
        assertEquals(5, calls)
    }

    @Test
    fun concurrentLoadsShareLookupAndInvalidationRejectsStaleCacheEntry() = runTest {
        val cache = IconCache<String, String>(2)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val first = async {
            cache.get("app") {
                calls++
                started.complete(Unit)
                finish.await()
                "old"
            }
        }
        started.await()
        cache.clear()
        finish.complete(Unit)
        assertEquals("old", first.await())
        val second = async {
            cache.get("app") {
                calls++
                "new"
            }
        }
        val third = async {
            cache.get("app") {
                calls++
                "duplicate"
            }
        }
        assertEquals("new", second.await())
        assertEquals("new", third.await())
        assertEquals(2, calls)
    }
}

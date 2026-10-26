package id.mybase.aether.packages

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bounded access-order cache. Null lookups are cached too; concurrent requests share one load. */
internal class IconCache<K, V>(private val capacity: Int) {
    init {
        require(capacity > 0)
    }

    private val values = LinkedHashMap<K, V?>(capacity, 0.75f, true)
    private val loading = Mutex()
    private var generation = 0L

    suspend fun get(key: K, loader: suspend () -> V?): V? = loading.withLock {
        val startGeneration =
            synchronized(values) {
                if (values.containsKey(key)) return@withLock values[key]
                generation
            }
        val value = loader()
        synchronized(values) {
            if (startGeneration == generation) {
                values[key] = value
                if (values.size > capacity) values.remove(values.keys.first())
            }
        }
        value
    }

    fun clear() =
        synchronized(values) {
            generation++
            values.clear()
        }
}

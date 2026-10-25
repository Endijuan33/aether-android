package id.mybase.aether

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.mybase.aether.adb.AndroidAdbKeyStore
import id.mybase.aether.persistence.SqliteOperationHistoryStore
import java.security.Signature
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformPersistenceTest {
    @Test
    fun sqliteFinalizationAndClearPreserveUnverifiedMarkers() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "aether_history_regression.db"
        context.deleteDatabase(databaseName)
        val store = SqliteOperationHistoryStore(context, databaseName)
        val pending =
            store.record(1, "com.example.app", "Example", "DISABLE", "IN_PROGRESS", "Unverified")
        store.clear()
        assertEquals(pending, store.list().single().id)
        store.complete(pending, "SUCCESS", "Verified")
        assertEquals("SUCCESS", store.list().single().status)
        store.clear()
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun androidKeystoreIdentityIsPersistentNonExportableAndSupportsTlsPss() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val first = AndroidAdbKeyStore(context).getOrCreateKeyPair()
        val second = AndroidAdbKeyStore(context).getOrCreateKeyPair()
        assertArrayEquals(first.public.encoded, second.public.encoded)
        assertNull(first.private.encoded)
        val signature = Signature.getInstance("SHA256withRSA/PSS")
        signature.initSign(first.private)
        signature.update("AETHER test".toByteArray())
        val signed = signature.sign()
        signature.initVerify(first.public)
        signature.update("AETHER test".toByteArray())
        assertTrue(signature.verify(signed))
    }
}

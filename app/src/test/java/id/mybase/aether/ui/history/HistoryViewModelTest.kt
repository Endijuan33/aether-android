package id.mybase.aether.ui.history

import app.cash.turbine.test
import id.mybase.aether.domain.OperationRecord
import id.mybase.aether.domain.OperationStatus
import id.mybase.aether.domain.OperationType
import id.mybase.aether.ui.fakes.FakeHistoryStore
import id.mybase.aether.ui.fakes.MainDispatcherRule
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * [HistoryViewModel] behavior: refresh loads the stored records, clear wipes them. Uses a
 * hand-faked [id.mybase.aether.persistence.OperationHistoryStore] and Turbine.
 */
class HistoryViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private lateinit var store: FakeHistoryStore
    private lateinit var vm: HistoryViewModel

    private fun record(
        id: Long,
        packageName: String,
        operation: OperationType,
        status: OperationStatus,
    ) =
        OperationRecord(
            id = id,
            timestamp = 1_700_000_000_000L + id,
            packageName = packageName,
            label = packageName,
            operation = operation.name,
            status = status.name,
            message = "ok",
        )

    @Before
    fun setUp() {
        store = FakeHistoryStore()
        vm = HistoryViewModel(store)
    }

    @Test
    fun `refresh loads stored records`() = runBlocking {
        store.seed(
            record(1, "com.a", OperationType.DISABLE, OperationStatus.SUCCESS),
            record(2, "com.b", OperationType.UNINSTALL_FOR_USER, OperationStatus.FAILED),
        )

        vm.refresh()

        vm.records.test(timeout = 5.seconds) {
            val records = awaitItem()
            assertEquals(2, records.size)
            assertEquals("com.a", records[0].packageName)
            assertEquals(OperationType.UNINSTALL_FOR_USER.name, records[1].operation)
            assertEquals(OperationStatus.FAILED.name, records[1].status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh with empty store emits empty list`() = runBlocking {
        vm.refresh()

        vm.records.test(timeout = 5.seconds) {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clear empties records and the store`() = runBlocking {
        store.seed(record(1, "com.a", OperationType.DISABLE, OperationStatus.SUCCESS))
        vm.refresh()

        vm.clear()

        vm.records.test(timeout = 5.seconds) {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(store.list().isEmpty())
    }
}

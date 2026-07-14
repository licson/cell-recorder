package com.cellrecorder.app.service

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BatchInsertStrategyTest {

    private sealed class FakeException(val transient: Boolean) : Throwable() {
        object TransientEx : FakeException(true)
        object FatalEx : FakeException(false)
    }

    private class FakeInserter(
        val itemCount: Int,
        val batchBehavior: (List<Int>) -> Unit = {},
        val singleBehavior: (Int) -> Unit = {}
    ) {
        var batchCallCount = 0
        var singleCallCount = 0
        val insertedItems = mutableListOf<Int>()

        suspend fun batchInsert(items: List<Int>) {
            batchCallCount++
            batchBehavior(items)
            // Default: success — caller checks callCount
        }

        suspend fun singleInsert(item: Int) {
            singleCallCount++
            singleBehavior(item)
            insertedItems.add(item)
        }
    }

    private fun isTransient(e: Throwable): Boolean =
        e is FakeException && e.transient

    private fun onFatal(e: Throwable): Nothing {
        throw FatalRecordingException("fatal: ${e.message}", e)
    }

    @Test
    fun `empty items returns zero without calling batch`() = runTest {
        var batchCalled = false
        val count = BatchInsertStrategy.execute<Int>(
            items = emptyList(),
            batchInsert = { batchCalled = true },
            singleInsert = { },
            isTransient = { true },
            onFatal = { throw it }
        )
        assertEquals(0, count)
        assertEquals(false, batchCalled)
    }

    @Test
    fun `batch success returns full count`() = runTest {
        val count = BatchInsertStrategy.execute(
            items = listOf(1, 2, 3, 4),
            batchInsert = { },
            singleInsert = { },
            isTransient = { _ -> true },
            onFatal = { throw it }
        )
        assertEquals(4, count)
    }

    @Test
    fun `transient batch throw with all per-snapshot succeeding returns full count`() = runTest {
        val count = BatchInsertStrategy.execute(
            items = listOf(1, 2, 3),
            batchInsert = { throw FakeException.TransientEx },
            singleInsert = { },
            isTransient = { it === FakeException.TransientEx },
            onFatal = { throw it }
        )
        assertEquals(3, count)
    }

    @Test
    fun `transient batch throw with some per-snapshot failing returns partial count`() = runTest {
        val count = BatchInsertStrategy.execute(
            items = listOf(1, 2, 3, 4, 5),
            batchInsert = { throw FakeException.TransientEx },
            singleInsert = { item ->
                if (item % 2 == 0) throw FakeException.TransientEx
            },
            isTransient = { it === FakeException.TransientEx },
            onFatal = { throw it }
        )
        assertEquals(3, count) // 1, 3, 5 succeed; 2, 4 skipped
    }

    @Test
    fun `transient batch throw with all per-snapshot failing returns zero`() = runTest {
        val count = BatchInsertStrategy.execute(
            items = listOf(1, 2, 3),
            batchInsert = { throw FakeException.TransientEx },
            singleInsert = { throw FakeException.TransientEx },
            isTransient = { it === FakeException.TransientEx },
            onFatal = { throw it }
        )
        assertEquals(0, count)
    }

    @Test
    fun `fatal batch throw invokes onFatal and propagates`() = runTest {
        val exception = assertThrows<FatalRecordingException> {
            BatchInsertStrategy.execute(
                items = listOf(1, 2, 3),
                batchInsert = { throw FakeException.FatalEx },
                singleInsert = { },
                isTransient = { it === FakeException.TransientEx },
                onFatal = { e ->
                    throw FatalRecordingException("fatal: ${e.message}", e)
                }
            )
        }
        assertEquals(FakeException.FatalEx, exception.cause)
    }

    @Test
    fun `CancellationException in batchInsert propagates without fallback`() = runTest {
        var singleCalled = false
        assertThrows<CancellationException> {
            BatchInsertStrategy.execute(
                items = listOf(1, 2, 3),
                batchInsert = { throw CancellationException("cancelled") },
                singleInsert = { singleCalled = true },
                isTransient = { false },
                onFatal = { throw it }
            )
        }
        assertEquals(false, singleCalled, "singleInsert must NOT be called when batch is cancelled")
    }

    @Test
    fun `CancellationException in singleInsert propagates and stops fallback`() = runTest {
        var secondItemInserted = false
        assertThrows<CancellationException> {
            BatchInsertStrategy.execute(
                items = listOf(1, 2, 3),
                batchInsert = { throw FakeException.TransientEx },
                singleInsert = { item ->
                    if (item == 1) throw CancellationException("cancelled")
                    if (item == 2) secondItemInserted = true
                },
                isTransient = { it === FakeException.TransientEx },
                onFatal = { throw it }
            )
        }
        assertEquals(false, secondItemInserted, "fallback must stop on CancellationException")
    }

    @Test
    fun `per-snapshot non-transient failure is skipped (not fatal) during fallback`() = runTest {
        // A fatal-looking exception thrown by singleInsert during fallback is SKIPPED, not
        // escalated to onFatal. onFatal only applies to the BATCH insert. This matches the
        // spec: per-snapshot failures are skipped and logged.
        var onFatalCalled = false
        val count = BatchInsertStrategy.execute(
            items = listOf(1, 2, 3),
            batchInsert = { throw FakeException.TransientEx },
            singleInsert = { item ->
                if (item == 2) throw FakeException.FatalEx
            },
            isTransient = { it === FakeException.TransientEx },
            onFatal = { onFatalCalled = true; throw it }
        )
        assertEquals(2, count)
        assertEquals(false, onFatalCalled, "onFatal must NOT be called for per-snapshot failures")
    }
}

package com.cellrecorder.app.service

import kotlinx.coroutines.CancellationException

/**
 * Pure-logic two-tier insert strategy. Attempts a single batched insert; on a transient
 * failure (per [isTransient]), falls back to per-item inserts, skipping individual
 * failures. On a fatal failure (not transient), invokes [onFatal] which must throw.
 *
 * Returns the number of successfully inserted items. Pure-logic — no Android or DB
 * dependencies; the inserter functions and classifier are injected, making this
 * fully unit-testable with fakes.
 *
 * [CancellationException] is always rethrown and never classified — structured
 * concurrency must be preserved through both the batch and per-snapshot paths.
 */
object BatchInsertStrategy {

    suspend fun <T> execute(
        items: List<T>,
        batchInsert: suspend (List<T>) -> Unit,
        singleInsert: suspend (T) -> Unit,
        isTransient: (Throwable) -> Boolean,
        onFatal: (Throwable) -> Nothing
    ): Int {
        if (items.isEmpty()) return 0
        return try {
            batchInsert(items)
            items.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!isTransient(e)) {
                onFatal(e)
            }
            var count = 0
            for (item in items) {
                try {
                    singleInsert(item)
                    count++
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    // Skip this item; per-snapshot failure is non-fatal.
                }
            }
            count
        }
    }
}

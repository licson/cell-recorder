package com.cellrecorder.app.service

import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.sql.SQLException

/**
 * Classifies a [Throwable] thrown by a database write as either [Classification.FATAL]
 * (recording must stop) or [Classification.TRANSIENT] (per-snapshot fallback or retry).
 *
 * [CancellationException] is rethrown and never classified — structured concurrency must
 * be preserved. Unknown exception types fail-open to [Classification.TRANSIENT] so that
 * an unforeseen error does not silently terminate a multi-hour recording.
 *
 * Pure-logic — no Android runtime required beyond the SQLite exception classes (which
 * are part of the platform). Unit-testable via direct construction of the exception types.
 */
object DbExceptionClassifier {

    enum class Classification { FATAL, TRANSIENT }

    private val FATAL_KEYWORDS = listOf("migration", "schema")

    fun classify(e: Throwable): Classification {
        when (e) {
            is CancellationException -> throw e
            is SQLiteFullException,
            is SQLiteReadOnlyDatabaseException -> return Classification.FATAL
            is SQLiteDatabaseLockedException -> return Classification.FATAL
            is IllegalStateException -> {
                val msg = e.message?.lowercase().orEmpty()
                if (FATAL_KEYWORDS.any { it in msg }) return Classification.FATAL
                return Classification.TRANSIENT
            }
            is SQLiteConstraintException,
            is SQLiteException,
            is IOException,
            is SQLException -> return Classification.TRANSIENT
            else -> {
                val cause = e.cause
                if (cause != null && cause !== e) return classify(cause)
                return Classification.TRANSIENT
            }
        }
    }
}

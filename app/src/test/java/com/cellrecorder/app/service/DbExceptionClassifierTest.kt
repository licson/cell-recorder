package com.cellrecorder.app.service

import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.sql.SQLException

class DbExceptionClassifierTest {

    @Test
    fun `SQLiteFullException is fatal`() {
        assertEquals(
            DbExceptionClassifier.Classification.FATAL,
            DbExceptionClassifier.classify(SQLiteFullException())
        )
    }

    @Test
    fun `SQLiteReadOnlyDatabaseException is fatal`() {
        assertEquals(
            DbExceptionClassifier.Classification.FATAL,
            DbExceptionClassifier.classify(SQLiteReadOnlyDatabaseException())
        )
    }

    @Test
    fun `SQLiteDatabaseLockedException is fatal`() {
        assertEquals(
            DbExceptionClassifier.Classification.FATAL,
            DbExceptionClassifier.classify(SQLiteDatabaseLockedException())
        )
    }

    @Test
    fun `IllegalStateException with migration message is fatal`() {
        val e = IllegalStateException("Migration from 5 to 6 required")
        assertEquals(
            DbExceptionClassifier.Classification.FATAL,
            DbExceptionClassifier.classify(e)
        )
    }

    @Test
    fun `IllegalStateException with schema message is fatal`() {
        val e = IllegalStateException("Schema mismatch detected")
        assertEquals(
            DbExceptionClassifier.Classification.FATAL,
            DbExceptionClassifier.classify(e)
        )
    }

    @Test
    fun `IllegalStateException without keyword is transient`() {
        val e = IllegalStateException("some other state issue")
        assertEquals(
            DbExceptionClassifier.Classification.TRANSIENT,
            DbExceptionClassifier.classify(e)
        )
    }

    @Test
    fun `SQLiteConstraintException is transient`() {
        assertEquals(
            DbExceptionClassifier.Classification.TRANSIENT,
            DbExceptionClassifier.classify(SQLiteConstraintException())
        )
    }

    @Test
    fun `generic SQLiteException is transient`() {
        assertEquals(
            DbExceptionClassifier.Classification.TRANSIENT,
            DbExceptionClassifier.classify(object : SQLiteException() {})
        )
    }

    @Test
    fun `IOException is transient`() {
        assertEquals(
            DbExceptionClassifier.Classification.TRANSIENT,
            DbExceptionClassifier.classify(IOException("disk I/O"))
        )
    }

    @Test
    fun `SQLException is transient`() {
        assertEquals(
            DbExceptionClassifier.Classification.TRANSIENT,
            DbExceptionClassifier.classify(SQLException("sql error"))
        )
    }

    @Test
    fun `unknown Exception type is transient - fail-open`() {
        assertEquals(
            DbExceptionClassifier.Classification.TRANSIENT,
            DbExceptionClassifier.classify(RuntimeException("something unexpected"))
        )
    }

    @Test
    fun `CancellationException is rethrown and never classified`() {
        assertThrows<CancellationException> {
            DbExceptionClassifier.classify(CancellationException("cancelled"))
        }
    }

    @Test
    fun `fatal exception wrapped in a generic RuntimeException is classified via cause`() {
        val fatal = SQLiteFullException()
        val wrapper = RuntimeException("wrapper", fatal)
        assertEquals(
            DbExceptionClassifier.Classification.FATAL,
            DbExceptionClassifier.classify(wrapper)
        )
    }

    @Test
    fun `transient exception wrapped in a generic RuntimeException is classified via cause`() {
        val transient = IOException("disk I/O")
        val wrapper = RuntimeException("wrapper", transient)
        assertEquals(
            DbExceptionClassifier.Classification.TRANSIENT,
            DbExceptionClassifier.classify(wrapper)
        )
    }
}

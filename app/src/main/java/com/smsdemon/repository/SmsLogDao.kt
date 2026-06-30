package com.smsdemon.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smsdemon.model.SmsLog
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for [SmsLog] entries.
 *
 * All queries are exposed as [Flow] so the log screen can react to
 * new inserts in real-time without manual polling.
 */
@Dao
interface SmsLogDao {

    /** Insert a new log entry. Returns the row-id of the inserted row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SmsLog): Long

    /**
     * Returns all log entries, newest first.
     * Emits a new list automatically whenever the table changes.
     */
    @Query("SELECT * FROM sms_log ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SmsLog>>

    /** Most recent entry – used by the status label. */
    @Query("SELECT * FROM sms_log ORDER BY timestamp DESC LIMIT 1")
    fun getLatest(): Flow<SmsLog?>

    /** Delete all log entries (used by the "Clear" action in LogActivity). */
    @Query("DELETE FROM sms_log")
    suspend fun clearAll()

    /** Total number of stored entries. */
    @Query("SELECT COUNT(*) FROM sms_log")
    suspend fun count(): Int
}

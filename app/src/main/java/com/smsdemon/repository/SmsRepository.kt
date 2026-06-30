package com.smsdemon.repository

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import com.smsdemon.model.SmsLog
import com.smsdemon.util.TemplateResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private const val TAG = "SmsRepository"

/**
 * Repository that owns all SMS-sending and log-persistence logic.
 *
 * Separated from the Service so that the business logic can be tested
 * independently of Android framework classes.
 *
 * @param context Application context used for [SmsManager] and Room.
 */
class SmsRepository(context: Context) {

    private val db     = AppDatabase.getInstance(context)
    private val dao    = db.smsLogDao()

    @Suppress("DEPRECATION")          // SmsManager.getDefault() still needed on API 29
    private val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)
        ?: SmsManager.getDefault()

    // ── Public API ─────────────────────────────────────────────────────────────

    /** All log entries as a reactive [Flow] (newest first). */
    val allLogs: Flow<List<SmsLog>> = dao.getAll()

    /** Latest single log entry as a reactive [Flow]. */
    val latestLog: Flow<SmsLog?> = dao.getLatest()

    /**
     * Resolves the template, sends the SMS via [SmsManager], and persists a
     * [SmsLog] entry regardless of success/failure.
     *
     * Must be called from a coroutine; the SMS send itself runs on [Dispatchers.IO].
     *
     * @param phoneNumber Destination number (pre-validated by caller).
     * @param template    Raw template string with optional placeholders.
     * @param counter     Monotonic send counter incremented by the service.
     * @return The persisted [SmsLog] row-id.
     */
    suspend fun sendSms(
        phoneNumber: String,
        template:    String,
        counter:     Int
    ): Long = withContext(Dispatchers.IO) {
        val resolved = TemplateResolver.resolve(template, counter)

        Log.d(TAG, "Sending SMS #$counter to $phoneNumber | msg=${resolved.message}")

        var success  = false
        var errorMsg: String? = null

        try {
            // sendTextMessage is synchronous from our perspective; actual delivery
            // confirmation would require a delivery PendingIntent (out of scope).
            smsManager.sendTextMessage(
                phoneNumber,
                null,
                resolved.message,
                null,
                null
            )
            success = true
            Log.i(TAG, "SMS #$counter sent successfully to $phoneNumber")
        } catch (e: Exception) {
            errorMsg = e.message ?: e.javaClass.simpleName
            Log.e(TAG, "SMS #$counter failed: $errorMsg", e)
        }

        val logEntry = SmsLog(
            phoneNumber  = phoneNumber,
            message      = resolved.message,
            randomValue  = resolved.randomValue,
            counter      = counter,
            timestamp    = System.currentTimeMillis(),
            success      = success,
            errorMsg     = errorMsg
        )

        dao.insert(logEntry)
    }

    /** Clears all log entries from the database. */
    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        dao.clearAll()
        Log.d(TAG, "Log database cleared")
    }
}

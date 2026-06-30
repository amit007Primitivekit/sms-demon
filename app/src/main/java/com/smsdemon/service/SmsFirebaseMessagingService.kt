package com.smsdemon.service

import android.content.SharedPreferences
import android.telephony.SmsManager
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.smsdemon.model.SmsLog
import com.smsdemon.repository.AppDatabase
import com.smsdemon.repository.BackendApiClient
import com.smsdemon.util.Constants
import com.smsdemon.util.NotificationHelper
import com.smsdemon.util.TemplateResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "SmsFirebaseMsgService"

/**
 * Handles incoming FCM data messages from the SMS Demon backend.
 *
 * ## Flow
 * 1. Backend POSTs `/api/commands` with { deviceId, phoneNumber, message }.
 * 2. Backend dispatches an FCM data message with type="send_sms" to this device.
 * 3. Android delivers the message here — even when the app is killed —
 *    because it is a DATA-only message (no `notification` key).
 * 4. We send the SMS via SmsManager, persist the log entry, then ACK the backend.
 *
 * Also handles token refresh: when FCM rotates the registration token, we
 * automatically re-register with the backend.
 */
class SmsFirebaseMessagingService : FirebaseMessagingService() {

    // Service-scoped coroutine scope — cancelled automatically when the service is destroyed
    private val serviceJob   = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
    }

    @Suppress("DEPRECATION")
    private val smsManager: SmsManager by lazy {
        getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
    }

    // ── Token refresh ─────────────────────────────────────────────────────────

    /**
     * Called by FCM whenever the registration token is refreshed.
     * We must re-register with the backend so it can reach this device.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "FCM token refreshed — re-registering with backend")

        serviceScope.launch {
            val backendUrl  = prefs.getString(Constants.PREF_BACKEND_URL, Constants.DEFAULT_BACKEND_URL)!!
            val deviceName  = android.os.Build.MODEL
            val client      = BackendApiClient(backendUrl)
            val deviceId    = client.registerDevice(token, deviceName)
            if (deviceId != null) {
                prefs.edit()
                    .putString(Constants.PREF_FCM_DEVICE_ID, deviceId)
                    .apply()
                Log.i(TAG, "Token re-registered, deviceId=$deviceId")
            }
        }
    }

    // ── Message receive ───────────────────────────────────────────────────────

    /**
     * Called when a data message arrives.
     *
     * Runs on a background thread; must complete within ~20 seconds (FCM limit).
     * We launch a coroutine and do the work there.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from=${remoteMessage.from}")

        val data = remoteMessage.data
        Log.d(TAG, "Data: $data")

        when (data[Constants.FCM_KEY_TYPE]) {
            Constants.FCM_VALUE_SEND_SMS -> handleSendSmsCommand(data)
            else -> Log.w(TAG, "Unknown FCM message type: ${data[Constants.FCM_KEY_TYPE]}")
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun handleSendSmsCommand(data: Map<String, String>) {
        val commandId   = data[Constants.FCM_KEY_COMMAND_ID]   ?: run { Log.e(TAG, "Missing commandId"); return }
        val phoneNumber = data[Constants.FCM_KEY_PHONE]        ?: run { Log.e(TAG, "Missing phoneNumber"); return }
        val rawMessage  = data[Constants.FCM_KEY_MESSAGE]      ?: run { Log.e(TAG, "Missing message"); return }

        Log.i(TAG, "handleSendSmsCommand commandId=$commandId phone=$phoneNumber")

        serviceScope.launch {
            // Resolve template placeholders (the message may contain {random} etc.)
            val resolved = TemplateResolver.resolve(rawMessage, counter = 0)
            val message  = resolved.message

            var success  = false
            var errorMsg: String? = null

            // ── Send the SMS ──────────────────────────────────────────────────
            try {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                success = true
                Log.i(TAG, "SMS sent to $phoneNumber via FCM command commandId=$commandId")
            } catch (e: Exception) {
                errorMsg = e.message ?: e.javaClass.simpleName
                Log.e(TAG, "SMS send failed commandId=$commandId: $errorMsg", e)
            }

            // ── Persist log ───────────────────────────────────────────────────
            try {
                val db  = AppDatabase.getInstance(applicationContext)
                val log = SmsLog(
                    phoneNumber  = phoneNumber,
                    message      = message,
                    randomValue  = resolved.randomValue,
                    counter      = 0,
                    timestamp    = System.currentTimeMillis(),
                    success      = success,
                    errorMsg     = errorMsg
                )
                db.smsLogDao().insert(log)
            } catch (e: Exception) {
                Log.e(TAG, "Log persist failed: ${e.message}", e)
            }

            // ── Update notification if foreground service is running ───────────
            try {
                val notifText = if (success)
                    "FCM → SMS sent to $phoneNumber"
                else
                    "FCM → SMS failed: $errorMsg"
                NotificationHelper.createChannel(applicationContext)
                NotificationHelper.update(applicationContext, notifText)
            } catch (_: Exception) { /* notification is best-effort */ }

            // ── ACK the backend ───────────────────────────────────────────────
            val backendUrl = prefs.getString(Constants.PREF_BACKEND_URL, Constants.DEFAULT_BACKEND_URL)!!
            BackendApiClient(backendUrl).ackCommand(commandId, success, errorMsg)
        }
    }
}

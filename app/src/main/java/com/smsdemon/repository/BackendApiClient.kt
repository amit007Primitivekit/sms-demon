package com.smsdemon.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "BackendApiClient"

/**
 * Lightweight HTTP client for the SMS Demon backend.
 *
 * Three responsibilities:
 *  1. [registerDevice]  — register FCM token on launch / token refresh
 *  2. [pong]            — reply to a backend presence ping as fast as possible
 *  3. [ackCommand]      — report SMS send result back to backend
 *
 * Online/offline state is determined entirely by the backend at request time
 * via FCM ping/pong — no heartbeats, no polling needed from the app side.
 */
class BackendApiClient(private val baseUrl: String) {

    private val json = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // ── Device registration ───────────────────────────────────────────────────

    /**
     * Registers (or refreshes) this device's FCM token with the backend.
     *
     * @param fcmToken   Current FCM registration token.
     * @param deviceName Human-readable name shown in the backend device list.
     * @return Backend-assigned device UUID, or null on failure.
     */
    suspend fun registerDevice(fcmToken: String, deviceName: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("fcmToken", fcmToken)
                    put("deviceName", deviceName)
                }.toString().toRequestBody(json)

                val request = Request.Builder()
                    .url("$baseUrl/api/devices/register")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "registerDevice HTTP ${response.code}")
                        return@withContext null
                    }
                    val obj      = JSONObject(response.body!!.string())
                    val deviceId = obj.getJSONObject("device").getString("id")
                    Log.i(TAG, "Device registered: id=$deviceId")
                    deviceId
                }
            } catch (e: Exception) {
                Log.e(TAG, "registerDevice: ${e.message}", e)
                null
            }
        }

    // ── Presence ──────────────────────────────────────────────────────────────

    /**
     * Responds to a backend presence ping as quickly as possible.
     *
     * The backend sends an FCM "ping" data message and waits at most ~4 s for
     * this HTTP POST.  Speed matters — call this immediately on receiving the
     * FCM message without any additional delay.
     *
     * @param deviceId Backend-assigned device UUID (included in the FCM payload).
     * @param pingId   Unique ID for this ping round (included in the FCM payload).
     */
    suspend fun pong(deviceId: String, pingId: String) = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("pingId", pingId)
            }.toString().toRequestBody(json)

            val request = Request.Builder()
                .url("$baseUrl/api/devices/$deviceId/pong")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Pong → deviceId=$deviceId pingId=$pingId HTTP=${response.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pong failed deviceId=$deviceId pingId=$pingId: ${e.message}")
        }
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    /**
     * Reports the SMS send result back to the backend after attempting to send.
     *
     * @param commandId Backend command UUID from the FCM data message.
     * @param success   Whether SmsManager.sendTextMessage succeeded.
     * @param errorMsg  Optional error description when success == false.
     */
    suspend fun ackCommand(
        commandId: String,
        success:   Boolean,
        errorMsg:  String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("success", success)
                if (errorMsg != null) put("errorMsg", errorMsg)
            }.toString().toRequestBody(json)

            val request = Request.Builder()
                .url("$baseUrl/api/commands/$commandId/ack")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "ACK commandId=$commandId success=$success")
                } else {
                    Log.w(TAG, "ACK HTTP ${response.code} commandId=$commandId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ackCommand commandId=$commandId: ${e.message}", e)
        }
    }
}

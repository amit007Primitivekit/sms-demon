package com.smsdemon.repository

import android.util.Log
import com.smsdemon.util.Constants
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
 * Uses OkHttp directly (no Retrofit) to keep dependencies minimal.
 * All calls run on [Dispatchers.IO].
 *
 * @param baseUrl Base URL of the backend, e.g. "https://smsdemon.virt.cc.cd"
 */
class BackendApiClient(private val baseUrl: String) {

    private val json = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Registers (or refreshes) the device's FCM token with the backend.
     *
     * @param fcmToken   Current FCM registration token.
     * @param deviceName Human-readable device name shown in the backend dashboard.
     * @return The backend-assigned device UUID, or null on failure.
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
                        Log.e(TAG, "registerDevice failed: HTTP ${response.code}")
                        return@withContext null
                    }
                    val obj = JSONObject(response.body!!.string())
                    val deviceId = obj.getJSONObject("device").getString("id")
                    Log.i(TAG, "Device registered: id=$deviceId")
                    deviceId
                }
            } catch (e: Exception) {
                Log.e(TAG, "registerDevice exception: ${e.message}", e)
                null
            }
        }

    /**
     * Sends an ACK to the backend after the device has attempted to send the SMS.
     *
     * @param commandId  The command UUID received in the FCM data message.
     * @param success    Whether [android.telephony.SmsManager] succeeded.
     * @param errorMsg   Optional error description if success == false.
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
                    Log.i(TAG, "ACK sent for commandId=$commandId success=$success")
                } else {
                    Log.w(TAG, "ACK failed: HTTP ${response.code} for commandId=$commandId")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ackCommand exception for commandId=$commandId: ${e.message}", e)
        }
    }
}

package com.smsdemon.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.smsdemon.util.Constants

private const val TAG = "BootReceiver"

/**
 * Restarts [SmsSenderService] after device reboot, if it was running before shutdown.
 *
 * Requires:
 *  - `RECEIVE_BOOT_COMPLETED` permission in the manifest.
 *  - The receiver declared with `android:exported="true"` and the matching intent filter.
 *
 * The service is only restarted if [Constants.PREF_SERVICE_RUNNING] is `true` in
 * SharedPreferences, preventing an unwanted auto-start on every boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val wasRunning = prefs.getBoolean(Constants.PREF_SERVICE_RUNNING, false)

        Log.d(TAG, "Boot received – service was running: $wasRunning")

        if (wasRunning) {
            val serviceIntent = Intent(context, SmsSenderService::class.java).apply {
                action = Constants.ACTION_START_SERVICE
            }
            context.startForegroundService(serviceIntent)
            Log.i(TAG, "SmsSenderService restarted after boot")
        }
    }
}

package com.smsdemon.service

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import com.smsdemon.repository.SmsRepository
import com.smsdemon.util.Constants
import com.smsdemon.util.NotificationHelper
import com.smsdemon.util.PhoneValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "SmsSenderService"

/**
 * Foreground Service that periodically sends SMS messages.
 *
 * ## Lifecycle
 * - Started with [Constants.ACTION_START_SERVICE] intent from MainActivity/BootReceiver.
 * - Stopped with [Constants.ACTION_STOP_SERVICE] intent (from MainActivity or the
 *   notification's "Stop" action button).
 * - The service promotes itself to foreground immediately in [onStartCommand] to
 *   survive background restrictions.
 *
 * ## Coroutine topology
 * A [SupervisorJob] scoped to the service lifetime owns a single child [Job] that
 * loops: send SMS → delay(interval) → repeat.  The child job is cancelled on stop;
 * the supervisor is cancelled in [onDestroy].
 */
class SmsSenderService : Service() {

    // ── Dependencies ─────────────────────────────────────────────────────────
    private lateinit var prefs:      SharedPreferences
    private lateinit var repository: SmsRepository

    // ── Coroutine machinery ───────────────────────────────────────────────────
    private val supervisorJob = SupervisorJob()
    private val serviceScope  = CoroutineScope(Dispatchers.Default + supervisorJob)
    private var smsLoopJob:   Job? = null

    // ── State ─────────────────────────────────────────────────────────────────
    /** Monotonically incrementing counter – persisted across service restarts. */
    private var sendCounter = 0

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        prefs      = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
        repository = SmsRepository(applicationContext)
        NotificationHelper.createChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")

        return when (intent?.action) {
            Constants.ACTION_STOP_SERVICE -> {
                stopSelf()
                START_NOT_STICKY
            }
            else -> {
                // Promote to foreground immediately so Android won't kill us
                val notification = NotificationHelper.buildNotification(
                    this,
                    getString(com.smsdemon.R.string.notification_starting)
                )
                startForeground(Constants.NOTIFICATION_ID, notification)

                // Mark running in prefs so the UI and BootReceiver can read the state
                prefs.edit().putBoolean(Constants.PREF_SERVICE_RUNNING, true).apply()

                startSmsLoop()

                // If the OS kills the service, restart it automatically
                START_STICKY
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy – cancelling SMS loop")
        smsLoopJob?.cancel()
        supervisorJob.cancel()
        prefs.edit().putBoolean(Constants.PREF_SERVICE_RUNNING, false).apply()
        super.onDestroy()
    }

    /** Not a bound service. */
    override fun onBind(intent: Intent?): IBinder? = null

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Launches (or re-launches) the coroutine that sends SMS on a fixed interval.
     *
     * The loop:
     *  1. Reads current config from SharedPreferences (so hot-changes work).
     *  2. Validates the phone number; logs & skips if invalid.
     *  3. Calls [SmsRepository.sendSms].
     *  4. Updates the foreground notification with send status.
     *  5. Delays for [intervalMs] before the next iteration.
     */
    private fun startSmsLoop() {
        smsLoopJob?.cancel()          // cancel any pre-existing loop
        smsLoopJob = serviceScope.launch {
            Log.i(TAG, "SMS loop started")

            while (true) {
                val phoneNumber = prefs.getString(Constants.PREF_PHONE_NUMBER, "") ?: ""
                val template    = prefs.getString(
                    Constants.PREF_SMS_TEMPLATE,
                    Constants.DEFAULT_SMS_TEMPLATE
                ) ?: Constants.DEFAULT_SMS_TEMPLATE
                val intervalMin = prefs.getInt(
                    Constants.PREF_INTERVAL_MINUTES,
                    Constants.DEFAULT_INTERVAL_MINUTES
                )
                val intervalMs  = intervalMin * 60_000L

                if (!PhoneValidator.isValid(phoneNumber)) {
                    Log.w(TAG, "Invalid phone number '$phoneNumber' – skipping send")
                    NotificationHelper.update(
                        this@SmsSenderService,
                        getString(com.smsdemon.R.string.notification_invalid_number)
                    )
                } else {
                    sendCounter++
                    repository.sendSms(
                        phoneNumber = PhoneValidator.normalise(phoneNumber),
                        template    = template,
                        counter     = sendCounter
                    )
                    NotificationHelper.update(
                        this@SmsSenderService,
                        getString(
                            com.smsdemon.R.string.notification_last_sent,
                            sendCounter,
                            intervalMin
                        )
                    )
                }

                Log.d(TAG, "Waiting ${intervalMin}m before next send")
                delay(intervalMs)
            }
        }
    }
}

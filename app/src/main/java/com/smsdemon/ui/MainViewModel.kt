package com.smsdemon.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.smsdemon.model.ServiceState
import com.smsdemon.util.Constants

/**
 * ViewModel for [MainActivity].
 *
 * Responsibilities:
 *  - Load / save SharedPreferences (phone number, template, interval).
 *  - Expose [serviceState] LiveData so the UI stays in sync with the service.
 *  - Provide helpers that MainActivity calls when the user presses Start / Stop.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)

    // ── Exposed state ─────────────────────────────────────────────────────────

    private val _serviceState = MutableLiveData<ServiceState>(readStateFromPrefs())
    /** Observed by MainActivity to update the status label and button enabled-states. */
    val serviceState: LiveData<ServiceState> = _serviceState

    // ── Preferences accessors ─────────────────────────────────────────────────

    fun loadPhoneNumber(): String =
        prefs.getString(Constants.PREF_PHONE_NUMBER, "") ?: ""

    fun loadTemplate(): String =
        prefs.getString(Constants.PREF_SMS_TEMPLATE, Constants.DEFAULT_SMS_TEMPLATE)
            ?: Constants.DEFAULT_SMS_TEMPLATE

    fun loadInterval(): Int =
        prefs.getInt(Constants.PREF_INTERVAL_MINUTES, Constants.DEFAULT_INTERVAL_MINUTES)

    /**
     * Persists all settings atomically and updates [serviceState] to [ServiceState.Running].
     * Called by MainActivity just before starting the service.
     */
    fun saveSettingsAndMarkRunning(phone: String, template: String, intervalMinutes: Int) {
        prefs.edit()
            .putString(Constants.PREF_PHONE_NUMBER, phone)
            .putString(Constants.PREF_SMS_TEMPLATE, template)
            .putInt(Constants.PREF_INTERVAL_MINUTES, intervalMinutes)
            .putBoolean(Constants.PREF_SERVICE_RUNNING, true)
            .apply()

        _serviceState.value = ServiceState.Running(intervalMinutes)
    }

    /** Persists stopped-state and updates LiveData. Called by MainActivity on Stop. */
    fun markStopped() {
        prefs.edit().putBoolean(Constants.PREF_SERVICE_RUNNING, false).apply()
        _serviceState.value = ServiceState.Stopped
    }

    /** Saves fields without changing the running state (e.g., when the user edits fields). */
    fun saveSettings(phone: String, template: String, intervalMinutes: Int) {
        prefs.edit()
            .putString(Constants.PREF_PHONE_NUMBER, phone)
            .putString(Constants.PREF_SMS_TEMPLATE, template)
            .putInt(Constants.PREF_INTERVAL_MINUTES, intervalMinutes)
            .apply()
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun readStateFromPrefs(): ServiceState {
        val running  = prefs.getBoolean(Constants.PREF_SERVICE_RUNNING, false)
        val interval = prefs.getInt(Constants.PREF_INTERVAL_MINUTES, Constants.DEFAULT_INTERVAL_MINUTES)
        return if (running) ServiceState.Running(interval) else ServiceState.Stopped
    }
}

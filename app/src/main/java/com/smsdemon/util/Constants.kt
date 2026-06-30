package com.smsdemon.util

/**
 * Application-wide constants.
 * Centralised here so magic strings never drift across files.
 */
object Constants {

    // ── SharedPreferences ─────────────────────────────────────────────────────
    const val PREFS_NAME            = "sms_demon_prefs"
    const val PREF_PHONE_NUMBER     = "phone_number"
    const val PREF_SMS_TEMPLATE     = "sms_template"
    const val PREF_INTERVAL_MINUTES = "interval_minutes"
    const val PREF_SERVICE_RUNNING  = "service_running"

    // ── Service Intent actions ────────────────────────────────────────────────
    const val ACTION_START_SERVICE  = "com.smsdemon.action.START"
    const val ACTION_STOP_SERVICE   = "com.smsdemon.action.STOP"

    // ── Notification ─────────────────────────────────────────────────────────
    const val NOTIFICATION_CHANNEL_ID   = "sms_sender_channel"
    const val NOTIFICATION_CHANNEL_NAME = "SMS Sender Service"
    const val NOTIFICATION_ID           = 1001

    // ── SMS template placeholders ─────────────────────────────────────────────
    const val PLACEHOLDER_RANDOM    = "{random}"
    const val PLACEHOLDER_TIMESTAMP = "{timestamp}"
    const val PLACEHOLDER_COUNTER   = "{counter}"

    // ── Defaults ─────────────────────────────────────────────────────────────
    const val DEFAULT_INTERVAL_MINUTES = 5
    const val DEFAULT_SMS_TEMPLATE =
        "OTP Test ID:{random} Time:{timestamp} Count:{counter}"
}

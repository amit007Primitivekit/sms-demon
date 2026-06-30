package com.smsdemon.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.smsdemon.R
import com.smsdemon.service.SmsSenderService
import com.smsdemon.ui.MainActivity

/**
 * Creates and updates the persistent foreground-service notification.
 *
 * The notification channel is created lazily on first access; Android
 * ignores duplicate channel creation calls, so this is safe to call
 * multiple times.
 */
object NotificationHelper {

    /**
     * Ensures the notification channel exists.
     * Must be called before posting any notification (e.g., in Application.onCreate
     * or before [buildNotification]).
     */
    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        if (manager.getNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW   // Low = no sound, but visible in tray
        ).apply {
            description = "Shows while the SMS sender service is active"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Builds the foreground notification shown while [SmsSenderService] is running.
     *
     * @param context      Application context.
     * @param contentText  Secondary line – shows interval or last-send status.
     */
    fun buildNotification(context: Context, contentText: String): Notification {
        // Tapping the notification opens MainActivity
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPi = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "Stop" action in the notification shade
        val stopIntent = Intent(context, SmsSenderService::class.java).apply {
            action = Constants.ACTION_STOP_SERVICE
        }
        val stopPi = PendingIntent.getService(
            context, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_sms_notification)
            .setOngoing(true)           // Cannot be swiped away
            .setContentIntent(openAppPi)
            .addAction(R.drawable.ic_stop, context.getString(R.string.action_stop), stopPi)
            .build()
    }

    /**
     * Updates an already-posted notification in place (no flicker).
     */
    fun update(context: Context, contentText: String) {
        val notification = buildNotification(context, contentText)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        manager.notify(Constants.NOTIFICATION_ID, notification)
    }
}

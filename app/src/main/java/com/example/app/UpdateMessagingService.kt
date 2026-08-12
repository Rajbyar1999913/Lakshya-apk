package com.example.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Receives update announcements sent to every installed Lakshya app. */
class UpdateMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        UpdateNotifications.subscribeToUpdateAnnouncements()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "Lakshya update available"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "A new version is ready. Tap Update Now to continue."

        showUpdateNotification(title, body)
    }

    private fun showUpdateNotification(title: String, body: String) {
        val channelId = "lakshya_app_updates"
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Lakshya app updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications about required Lakshya updates"
                }
            )
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            901,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(901, notification)
    }
}

object UpdateNotifications {

    const val UPDATE_TOPIC = "lakshya_app_updates"

    /** All app installations receive broadcasts sent to this Firebase topic. */
    fun subscribeToUpdateAnnouncements() {
        FirebaseMessaging.getInstance().subscribeToTopic(UPDATE_TOPIC)
    }
}

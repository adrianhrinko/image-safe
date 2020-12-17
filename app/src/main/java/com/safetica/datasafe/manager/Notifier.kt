package com.safetica.datasafe.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.safetica.datasafe.R
import com.safetica.datasafe.activity.GalleryActivity

/**
 * Implements methods that show notifications
 */
object Notifier {
    private const val PENDING_INTENT_ID = 1112
    private const val NOTIFICATION_CHANNEL_ID = "com.safetica.imagesafe.NOTIFICATION_CHANEL"

    const val ENCRYPTION_NOTIFICATION_ID = 7371



    fun notify(context: Context, id: Int, titleId: Int, messageId: Int, activity: Class<*>? = null, bundle: Bundle? = null, permanent: Boolean = false, autoCancel: Boolean = true) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mChannel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(mChannel)
        }

        val notificationBuilder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setColor(ContextCompat.getColor(context, R.color.colorPrimary))
                .setSmallIcon(R.drawable.about_app)
                .setContentTitle(context.getString(titleId))
                .setContentText(context.getString(messageId))
                .setDefaults(Notification.DEFAULT_VIBRATE)
                .setOngoing(permanent)
                .setAutoCancel(autoCancel)

        if (activity != null) {
            notificationBuilder.setContentIntent(contentIntent(context, activity, bundle))
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notificationBuilder.priority = NotificationCompat.PRIORITY_HIGH
        }
        notificationManager.notify(id, notificationBuilder.build())
    }

    private fun contentIntent(context: Context, cls: Class<*>, bundle: Bundle? = null): PendingIntent {
        val startActivityIntent = Intent(context, cls)
        if (bundle != null) {
            startActivityIntent.putExtras(bundle)
        }
        return PendingIntent.getActivity(
                context,
                PENDING_INTENT_ID,
                startActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun cancelNotification(context: Context, id: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(id)
    }

    fun pushEncryptonNotification(context: Context, bundle: Bundle? = null) {
        notify(context, ENCRYPTION_NOTIFICATION_ID, R.string.app_name, R.string.encrypt, GalleryActivity::class.java, bundle)
    }
}
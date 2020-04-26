package dev.sasikanth.pinnit.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.sasikanth.pinnit.R
import dev.sasikanth.pinnit.activity.MainActivity
import dev.sasikanth.pinnit.data.PinnitNotification

object NotificationUtil {

  private const val CHANNEL_ID = "pinned_notifications"

  fun showNotification(context: Context, pinnitNotification: PinnitNotification) {
    createNotificationChannel(context)
    val notification = buildSystemNotification(context, pinnitNotification)

    with(NotificationManagerCompat.from(context)) {
      notify(pinnitNotification.uuid.hashCode(), notification)
    }
  }

  fun dismissNotification(context: Context, pinnitNotification: PinnitNotification) {
    NotificationManagerCompat.from(context).cancel(pinnitNotification.uuid.hashCode())
  }

  private fun buildSystemNotification(context: Context, notification: PinnitNotification): Notification {
    val content = notification.content.orEmpty()

    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, 0)

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_pinnit_notification)
      .setContentTitle(notification.title)
      .setContentText(content)
      .setContentIntent(pendingIntent)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setOngoing(true)

    return builder.build()
  }

  private fun createNotificationChannel(context: Context) {
    // Create the NotificationChannel, but only on API 26+ because
    // the NotificationChannel class is new and not in the support library
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val name = context.getString(R.string.channel_name)
      val importance = NotificationManager.IMPORTANCE_LOW
      val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
        setShowBadge(false)
        enableLights(false)
      }
      // Register the channel with the system
      val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      notificationManager.createNotificationChannel(channel)
    }
  }
}
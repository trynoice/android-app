package com.github.ashutoshgngwr.noice.engine.exoplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.github.ashutoshgngwr.noice.R

object SoundDownloadsNotificationChannelHelper {

  internal const val CHANNEL_ID = "com.github.ashutoshgngwr.noice.sound_downloads"

  internal fun initChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }

    val channelName = context.getString(R.string.notification_channel_sound_downloads__name)
    NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW)
      .apply {
        description = context.getString(R.string.notification_channel_sound_downloads__description)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        setShowBadge(false)
      }
      .also { NotificationManagerCompat.from(context).createNotificationChannel(it) }
  }
}

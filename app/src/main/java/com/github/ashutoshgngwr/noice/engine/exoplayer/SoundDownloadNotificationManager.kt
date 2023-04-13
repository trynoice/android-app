package com.github.ashutoshgngwr.noice.engine.exoplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import com.github.ashutoshgngwr.noice.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class SoundDownloadNotificationManager @Inject constructor(@ApplicationContext context: Context) {

  val exoPlayerNotificationHelper = DownloadNotificationHelper(context, CHANNEL_ID)
  val refreshWorkerNotification = NotificationCompat.Builder(context, CHANNEL_ID)
    .setContentTitle(context.getString(R.string.checking_sound_downloads))
    .setTicker(context.getString(R.string.checking_sound_downloads))
    .setProgress(0, 0, true)
    .setSmallIcon(R.drawable.launcher_24)
    .setOngoing(true)
    .setShowWhen(false)
    .setSilent(true)
    .build()

  init {
    initChannel(context)
  }

  private fun initChannel(context: Context) {
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

  companion object {
    private const val CHANNEL_ID = "com.github.ashutoshgngwr.noice.sound_downloads"
  }
}

package com.github.ashutoshgngwr.noice.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.github.ashutoshgngwr.noice.R

object PlayerNotificationManager {

  private const val NOTIFICATION_CHANNEL_ID = "com.github.ashutoshgngwr.noice.default"
  private const val RC_RESUME_PLAYBACK = 0x10
  private const val RC_PAUSE_PLAYBACK = 0x11
  private const val RC_STOP_PLAYBACK = 0x12

  fun createChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }

    val channel = NotificationChannel(
      NOTIFICATION_CHANNEL_ID,
      context.getString(R.string.notification_channel_default__name),
      NotificationManager.IMPORTANCE_LOW
    )

    channel.description = context.getString(R.string.notification_channel_default__description)
    channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    channel.setShowBadge(false)
    NotificationManagerCompat.from(context)
      .createNotificationChannel(channel)
  }

  fun createNotification(context: Context, mediaSession: MediaSessionCompat, title: String): Notification {
    return with(NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)) {
      color = ContextCompat.getColor(context, R.color.primary_dark)
      setContentTitle(title)
      setSmallIcon(R.drawable.ic_launcher_24dp)
      setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      setOngoing(true)
      setContentIntent(mediaSession.controller.sessionActivity)
      setDeleteIntent(
        PlaybackController.buildNotificationActionIntent(
          context, PlaybackController.ACTION_STOP_PLAYBACK, RC_STOP_PLAYBACK
        )
      )

      setStyle(
        androidx.media.app.NotificationCompat.MediaStyle().also {
          it.setShowActionsInCompactView(0)
          it.setMediaSession(mediaSession.sessionToken)
        }
      )

      if (PlaybackStateCompat.STATE_PLAYING == mediaSession.controller.playbackState?.state) {
        addAction(
          NotificationCompat.Action(
            R.drawable.ic_noti_pause,
            context.getString(R.string.pause),
            PlaybackController.buildNotificationActionIntent(
              context,
              PlaybackController.ACTION_PAUSE_PLAYBACK,
              RC_PAUSE_PLAYBACK
            )
          )
        )
      } else {
        addAction(
          NotificationCompat.Action(
            R.drawable.ic_noti_play,
            context.getString(R.string.play),
            PlaybackController.buildNotificationActionIntent(
              context,
              PlaybackController.ACTION_RESUME_PLAYBACK,
              RC_RESUME_PLAYBACK
            )
          )
        )
      }

      addAction(
        NotificationCompat.Action(
          R.drawable.ic_noti_close,
          context.getString(R.string.stop),
          PlaybackController.buildNotificationActionIntent(
            context,
            PlaybackController.ACTION_STOP_PLAYBACK,
            RC_STOP_PLAYBACK
          )
        )
      )

      build()
    }
  }
}

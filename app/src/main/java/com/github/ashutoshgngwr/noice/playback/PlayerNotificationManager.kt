package com.github.ashutoshgngwr.noice.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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

  fun createNotification(
    context: Context,
    mediaSession: MediaSessionCompat,
    title: String,
    enableSkipButtons: Boolean,
  ): Notification {
    return with(NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)) {
      color = ContextCompat.getColor(context, R.color.primary_dark)
      setContentTitle(title)
      setSmallIcon(R.drawable.ic_launcher_24dp)
      setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      setShowWhen(false)
      setContentIntent(mediaSession.controller.sessionActivity)
      setDeleteIntent(
        PlaybackController.buildStopActionPendingIntent(context)
      )

      setStyle(
        androidx.media.app.NotificationCompat.MediaStyle().also {
          it.setShowActionsInCompactView(0, 1, 2)
          it.setMediaSession(mediaSession.sessionToken)
        }
      )

      var skipPrevIntent: PendingIntent? = null
      if (enableSkipButtons) {
        skipPrevIntent = PlaybackController.buildSkipPrevActionPendingIntent(context)
      }

      addAction(
        NotificationCompat.Action(
          R.drawable.ic_noti_prev,
          context.getString(R.string.skip_to_prev),
          skipPrevIntent
        )
      )

      if (PlaybackStateCompat.STATE_PLAYING == mediaSession.controller.playbackState?.state) {
        addAction(
          NotificationCompat.Action(
            R.drawable.ic_noti_pause,
            context.getString(R.string.pause),
            PlaybackController.buildPauseActionPendingIntent(context)
          )
        )
      } else {
        addAction(
          NotificationCompat.Action(
            R.drawable.ic_noti_play,
            context.getString(R.string.play),
            PlaybackController.buildResumeActionPendingIntent(context)
          )
        )
      }

      var skipNextIntent: PendingIntent? = null
      if (enableSkipButtons) {
        skipNextIntent = PlaybackController.buildSkipNextActionPendingIntent(context)
      }

      addAction(
        NotificationCompat.Action(
          R.drawable.ic_noti_next,
          context.getString(R.string.skip_to_next),
          skipNextIntent
        )
      )

      addAction(
        NotificationCompat.Action(
          R.drawable.ic_noti_close,
          context.getString(R.string.stop),
          PlaybackController.buildStopActionPendingIntent(context)
        )
      )

      build()
    }
  }
}

package com.github.ashutoshgngwr.noice.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.model.Preset

/**
 * Manages a media-styled notification displayed by the playback service when it runs in the
 * foreground.
 */
class PlaybackNotificationManager(
  context: Context,
  contentIntentPi: PendingIntent,
  mediaSessionToken: MediaSessionCompat.Token,
) {

  private val defaultTitle = context.getString(R.string.unsaved_preset)
  private val stateTexts = mapOf(
    PlaybackState.PLAYING to context.getString(R.string.playing),
    PlaybackState.PAUSING to context.getString(R.string.pausing),
    PlaybackState.PAUSED to context.getString(R.string.paused),
    PlaybackState.STOPPING to context.getString(R.string.stopping),
  )

  private val notificationStyle = MediaStyle()
    .setMediaSession(mediaSessionToken)

  private val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID).apply {
    color = ContextCompat.getColor(context, R.color.md_theme_primary)
    setContentIntent(contentIntentPi)
    setDeleteIntent(PlaybackController.buildStopActionPendingIntent(context))
    setShowWhen(false)
    setSilent(true)
    setSmallIcon(R.drawable.ic_launcher_24dp)
    setStyle(notificationStyle)
    setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
  }

  private val randomPresetAction = NotificationCompat.Action(
    R.drawable.ic_baseline_shuffle_32,
    context.getString(R.string.random_preset),
    PlaybackController.buildRandomPresetActionPendingIntent(context)
  )

  private val skipPrevAction = NotificationCompat.Action(
    R.drawable.ic_baseline_skip_previous_32,
    context.getString(R.string.skip_to_prev),
    PlaybackController.buildSkipPrevActionPendingIntent(context)
  )

  private val skipNextAction = NotificationCompat.Action(
    R.drawable.ic_baseline_skip_next_32,
    context.getString(R.string.skip_to_next),
    PlaybackController.buildSkipNextActionPendingIntent(context)
  )

  private val pauseAction = NotificationCompat.Action(
    R.drawable.ic_baseline_pause_32,
    context.getString(R.string.pause),
    PlaybackController.buildPauseActionPendingIntent(context)
  )

  private val playAction = NotificationCompat.Action(
    R.drawable.ic_baseline_play_arrow_32,
    context.getString(R.string.play),
    PlaybackController.buildResumeActionPendingIntent(context)
  )

  private val closeAction = NotificationCompat.Action(
    R.drawable.ic_baseline_close_32,
    context.getString(R.string.stop),
    PlaybackController.buildStopActionPendingIntent(context)
  )

  init {
    initChannel(context)
  }

  /**
   * Creates the notification from the given [state] and [currentPreset]. It adds skip preset action
   * buttons to the notification only if [currentPreset] is not null. Otherwise, it adds the random
   * preset action button instead.
   */
  fun createNotification(state: PlaybackState, currentPreset: Preset?): Notification {
    return notificationBuilder.run {
      setContentTitle(currentPreset?.name ?: defaultTitle)
      setContentText(stateTexts[state])
      clearActions()
      if (currentPreset == null) {
        addAction(randomPresetAction)
      } else {
        addAction(skipPrevAction)
      }

      if (
        state.oneOf(
          PlaybackState.PAUSED,
          PlaybackState.PAUSING,
          PlaybackState.STOPPED,
          PlaybackState.STOPPING,
        )
      ) {
        addAction(playAction)
      } else {
        addAction(pauseAction)
      }

      var actionCount = 2
      if (currentPreset != null) {
        addAction(skipNextAction)
        actionCount++
      }

      if (state != PlaybackState.STOPPING) {
        addAction(closeAction)
        actionCount++
      }

      if (actionCount > 2) {
        notificationStyle.setShowActionsInCompactView(0, 1, 2)
      } else {
        notificationStyle.setShowActionsInCompactView(0, 1)
      }

      build()
    }
  }

  private fun initChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return
    }

    val channelName = context.getString(R.string.notification_channel_default__name)
    NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_LOW)
      .apply {
        description = context.getString(R.string.notification_channel_default__description)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        setShowBadge(false)
      }
      .also { NotificationManagerCompat.from(context).createNotificationChannel(it) }
  }

  companion object {
    private const val CHANNEL_ID = "com.github.ashutoshgngwr.noice.default"
  }
}

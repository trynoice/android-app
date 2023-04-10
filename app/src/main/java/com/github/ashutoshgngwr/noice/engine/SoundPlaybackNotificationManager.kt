package com.github.ashutoshgngwr.noice.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.media.app.NotificationCompat.MediaStyle
import com.github.ashutoshgngwr.noice.R

/**
 * Manages a media-styled notification displayed by the playback service when it runs in the
 * foreground.
 */
class SoundPlaybackNotificationManager(
  private val service: Service,
  mediaSessionToken: MediaSessionCompat.Token,
  contentPi: PendingIntent,
  resumePi: PendingIntent,
  pausePi: PendingIntent,
  stopPi: PendingIntent,
  randomPresetPi: PendingIntent,
  skipToNextPresetPi: PendingIntent,
  skipToPrevPresetPi: PendingIntent,
) {

  private var isServiceInForeground = false
  private val defaultTitle = service.getString(R.string.unsaved_preset)
  private val notificationManager: NotificationManager = requireNotNull(service.getSystemService())
  private val stateTexts = mapOf(
    SoundPlayerManager.State.PLAYING to service.getString(R.string.playing),
    SoundPlayerManager.State.PAUSING to service.getString(R.string.pausing),
    SoundPlayerManager.State.PAUSED to service.getString(R.string.paused),
    SoundPlayerManager.State.STOPPING to service.getString(R.string.stopping),
  )

  private val style = MediaStyle()
    .setMediaSession(mediaSessionToken)

  private val builder = NotificationCompat.Builder(service, CHANNEL_ID).apply {
    color = ContextCompat.getColor(service, R.color.md_theme_primary)
    setContentIntent(contentPi)
    setDeleteIntent(stopPi)
    setOngoing(true)
    setShowWhen(false)
    setSilent(true)
    setSmallIcon(R.drawable.launcher_24)
    setStyle(style)
    setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
  }

  private val randomPresetAction = NotificationCompat.Action(
    R.drawable.round_shuffle_32,
    service.getString(R.string.random_preset),
    randomPresetPi,
  )

  private val skipPrevAction = NotificationCompat.Action(
    R.drawable.round_skip_previous_32,
    service.getString(R.string.skip_to_prev),
    skipToPrevPresetPi,
  )

  private val skipNextAction = NotificationCompat.Action(
    R.drawable.round_skip_next_32,
    service.getString(R.string.skip_to_next),
    skipToNextPresetPi,
  )

  private val pauseAction = NotificationCompat.Action(
    R.drawable.round_pause_32,
    service.getString(R.string.pause),
    pausePi,
  )

  private val playAction = NotificationCompat.Action(
    R.drawable.round_play_arrow_32,
    service.getString(R.string.play),
    resumePi,
  )

  private val closeAction = NotificationCompat.Action(
    R.drawable.round_close_32,
    service.getString(R.string.stop),
    stopPi,
  )

  private var soundPlayerManagerState = SoundPlayerManager.State.STOPPED
  private var currentPresetName = defaultTitle

  init {
    initChannel(service)
  }

  fun setState(state: SoundPlayerManager.State) {
    soundPlayerManagerState = state
    builder.setContentText(stateTexts[state])
    updateForegroundNotification()
  }

  fun setCurrentPresetName(name: String?) {
    currentPresetName = name ?: defaultTitle
    builder.setContentTitle(currentPresetName)
    updateForegroundNotification()
  }

  private fun updateForegroundNotification() {
    if (soundPlayerManagerState == SoundPlayerManager.State.STOPPED) {
      if (isServiceInForeground) {
        isServiceInForeground = false
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
      }
      return
    }

    builder.clearActions()
    if (currentPresetName == defaultTitle) {
      builder.addAction(randomPresetAction)
    } else {
      builder.addAction(skipPrevAction)
    }

    if (soundPlayerManagerState != SoundPlayerManager.State.PLAYING) {
      builder.addAction(playAction)
    } else {
      builder.addAction(pauseAction)
    }

    var actionCount = 2
    if (currentPresetName != defaultTitle) {
      builder.addAction(skipNextAction)
      actionCount++
    }

    if (soundPlayerManagerState != SoundPlayerManager.State.STOPPING) {
      builder.addAction(closeAction)
      actionCount++
    }

    if (actionCount > 2) {
      style.setShowActionsInCompactView(0, 1, 2)
    } else {
      style.setShowActionsInCompactView(0, 1)
    }

    if (isServiceInForeground) {
      notificationManager.notify(NOTIFICATION_ID, builder.build())
    } else {
      isServiceInForeground = true
      service.startForeground(NOTIFICATION_ID, builder.build())
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

    @VisibleForTesting
    const val NOTIFICATION_ID = 0x01
  }
}

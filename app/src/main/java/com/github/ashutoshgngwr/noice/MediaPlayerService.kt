package com.github.ashutoshgngwr.noice

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MediaPlayerService : Service() {

  companion object {
    const val FOREGROUND_ID = 0x29
    const val RC_MAIN_ACTIVITY = 0x28
    const val RC_START_PLAYBACK = 0x27
    const val RC_STOP_PLAYBACK = 0x26
    const val RC_STOP_SERVICE = 0x25
    const val RC_UPDATE_NOTIFICATION = 0x24

    const val NOTIFICATION_CHANNEL_ID = "com.github.ashutoshgngwr.noice.default"
  }

  private lateinit var mSoundManager: SoundManager

  inner class PlaybackBinder : Binder() {
    fun getSoundManager(): SoundManager = this@MediaPlayerService.mSoundManager
  }

  override fun onBind(intent: Intent?): IBinder? {
    return PlaybackBinder()
  }

  override fun onCreate() {
    super.onCreate()
    mSoundManager = SoundManager(this)
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.getIntExtra("action", 0)) {
      RC_START_PLAYBACK -> {
        mSoundManager.resumePlayback()
        startForeground(FOREGROUND_ID, updateNotification())
      }

      RC_STOP_PLAYBACK -> {
        mSoundManager.pausePlayback()
        startForeground(FOREGROUND_ID, updateNotification())
      }

      RC_UPDATE_NOTIFICATION -> {
        startForeground(FOREGROUND_ID, updateNotification())
      }

      RC_STOP_SERVICE -> {
        mSoundManager.stop()
        stopForeground(true)
      }
    }

    return START_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
    mSoundManager.release()
  }

  private fun updateNotification(): Notification {
    val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
      .setContentTitle(getString(R.string.app_name))
      .setShowWhen(false)
      .setSmallIcon(R.drawable.ic_stat_media_player)
      .setContentIntent(
        PendingIntent.getActivity(
          this, RC_MAIN_ACTIVITY,
          Intent(this, MainActivity::class.java),
          PendingIntent.FLAG_UPDATE_CURRENT
        )
      )
      .addAction(
        R.drawable.ic_stat_close,
        getString(R.string.stop),
        createPlaybackControlPendingIntent(RC_STOP_SERVICE)
      )

    if (mSoundManager.isPlaying) {
      notificationBuilder.addAction(
        R.drawable.ic_stat_pause,
        getString(R.string.pause),
        createPlaybackControlPendingIntent(RC_STOP_PLAYBACK)
      )
    } else {
      notificationBuilder.addAction(
        R.drawable.ic_stat_play,
        getString(R.string.play),
        createPlaybackControlPendingIntent(RC_START_PLAYBACK)
      )
    }

    return notificationBuilder.build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        NOTIFICATION_CHANNEL_ID,
        getString(R.string.notification_channel_default__name),
        NotificationManager.IMPORTANCE_MIN
      )

      channel.description = getString(R.string.notification_channel_default__description)
      channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      channel.setShowBadge(false)

      val notificationManager = getSystemService(NotificationManager::class.java)!!
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun createPlaybackControlPendingIntent(requestCode: Int): PendingIntent {
    return PendingIntent.getService(
      this,
      requestCode,
      Intent(this, MediaPlayerService::class.java)
        .putExtra("action", requestCode),
      PendingIntent.FLAG_UPDATE_CURRENT
    )
  }
}

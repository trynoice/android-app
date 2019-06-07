package com.github.ashutoshgngwr.noice

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MediaPlayerService : Service(), SoundManager.OnPlaybackStateChangeListener,
  AudioManager.OnAudioFocusChangeListener {

  companion object {
    const val FOREGROUND_ID = 0x29
    const val RC_MAIN_ACTIVITY = 0x28
    const val RC_START_PLAYBACK = 0x27
    const val RC_STOP_PLAYBACK = 0x26
    const val RC_STOP_SERVICE = 0x25

    const val NOTIFICATION_CHANNEL_ID = "com.github.ashutoshgngwr.noice.default"
  }

  private lateinit var mAudioManager: AudioManager
  private lateinit var mSoundManager: SoundManager

  private val becomingNoisyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
        mSoundManager.pausePlayback()
      }
    }
  }

  inner class PlaybackBinder : Binder() {
    fun getSoundManager(): SoundManager = this@MediaPlayerService.mSoundManager
  }

  override fun onBind(intent: Intent?): IBinder? {
    return PlaybackBinder()
  }

  override fun onCreate() {
    super.onCreate()
    mAudioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    mSoundManager = SoundManager(this)
    mSoundManager.addOnPlaybackStateChangeListener(this)
    createNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.getIntExtra("action", 0)) {
      RC_START_PLAYBACK -> {
        mSoundManager.resumePlayback()
      }

      RC_STOP_PLAYBACK -> {
        mSoundManager.pausePlayback()
      }

      RC_STOP_SERVICE -> {
        mSoundManager.stop()
      }
    }

    return START_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
    mSoundManager.removeOnPlaybackStateChangeListener(this)
    mSoundManager.release()
  }

  @Suppress("DEPRECATION")
  override fun onPlaybackStateChanged() {
    if (mSoundManager.isPlaying || mSoundManager.isPaused()) {
      startForeground(FOREGROUND_ID, updateNotification())
    } else {
      stopForeground(true)
    }

    if (mSoundManager.isPlaying) {
      // playback started, request audio focus
      mAudioManager.requestAudioFocus(
        this,
        AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
      )

      // register becoming noisy receiver to detect audio output config changes
      registerReceiver(
        becomingNoisyReceiver,
        IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
      )
    } else {
      // release audio focus
      mAudioManager.abandonAudioFocus(this)

      // unregister receiver
      unregisterReceiver(becomingNoisyReceiver)
    }
  }

  override fun onAudioFocusChange(focusChange: Int) {
    when (focusChange) {
      AudioManager.AUDIOFOCUS_LOSS -> {
        mSoundManager.pausePlayback()
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        mSoundManager.pausePlayback()
      }
      AudioManager.AUDIOFOCUS_GAIN -> {
        mSoundManager.resumePlayback()
      }
    }
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
    } else if (mSoundManager.isPaused()) {
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

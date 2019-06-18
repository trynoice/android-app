package com.github.ashutoshgngwr.noice

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat

class MediaPlayerService : Service(), SoundManager.OnPlaybackStateChangeListener,
  AudioManager.OnAudioFocusChangeListener {

  companion object {
    const val TAG = "MediaPlayerService"

    const val FOREGROUND_ID = 0x29
    const val RC_MAIN_ACTIVITY = 0x28
    const val RC_START_PLAYBACK = 0x27
    const val RC_STOP_PLAYBACK = 0x26
    const val RC_STOP_SERVICE = 0x25

    const val NOTIFICATION_CHANNEL_ID = "com.github.ashutoshgngwr.noice.default"
  }

  private lateinit var mAudioManager: AudioManager
  private lateinit var mSoundManager: SoundManager

  private var playbackDelayed = false
  private var resumeOnFocusGain = false

  private val mAudioAttributes = AudioAttributes.Builder()
    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
    .setLegacyStreamType(AudioManager.STREAM_MUSIC)
    .setUsage(AudioAttributes.USAGE_GAME)
    .build()

  @RequiresApi(Build.VERSION_CODES.O)
  private val mAudioFocusRequest =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      null
    } else {
      AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(mAudioAttributes)
        .setAcceptsDelayedFocusGain(true)
        .setOnAudioFocusChangeListener(this, Handler())
        .setWillPauseWhenDucked(false)
        .build()
    }

  private val becomingNoisyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && mSoundManager.isPlaying) {
        Log.i(TAG, "Becoming noisy... Pause playback!")
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
    mSoundManager = SoundManager(this, mAudioAttributes)
    mSoundManager.addOnPlaybackStateChangeListener(this)
    createNotificationChannel()

    // register becoming noisy receiver to detect audio output config changes
    registerReceiver(
      becomingNoisyReceiver,
      IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    )
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
        mSoundManager.stopPlayback()
      }
    }

    return START_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()

    // unregister receiver and listener. release sound pool resources
    unregisterReceiver(becomingNoisyReceiver)
    mSoundManager.removeOnPlaybackStateChangeListener(this)
    mSoundManager.release()
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    when (playbackState) {
      SoundManager.OnPlaybackStateChangeListener.STATE_PLAYBACK_STOPPED -> {
        Log.d(TAG, "Playback stopped! Remove service from foreground....")
        stopForeground(true)
      }
      SoundManager.OnPlaybackStateChangeListener.STATE_PLAYBACK_STARTED,
      SoundManager.OnPlaybackStateChangeListener.STATE_PLAYBACK_PAUSED,
      SoundManager.OnPlaybackStateChangeListener.STATE_PLAYBACK_RESUMED -> {
        Log.d(TAG, "Playback is not in stopped state! Update notification...")
        startForeground(FOREGROUND_ID, updateNotification())
      }
    }

    if (playbackState == SoundManager.OnPlaybackStateChangeListener.STATE_PLAYBACK_STARTED) {
      Log.d(TAG, "Playback started! Request audio focus in-case we don't have it...")
      handleAudioFocusRequestResult(createAudioFocusRequest())
    } else if (!playbackDelayed && playbackState == SoundManager.OnPlaybackStateChangeListener.STATE_PLAYBACK_STOPPED) {
      Log.d(TAG, "Playback is neither delayed nor paused or playing; abandon audio focus...")
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        @Suppress("DEPRECATION")
        mAudioManager.abandonAudioFocus(this)
      } else {
        mAudioFocusRequest ?: return
        mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest)
      }
    }
  }

  override fun onAudioFocusChange(focusChange: Int) {
    when (focusChange) {
      AudioManager.AUDIOFOCUS_GAIN -> {
        Log.d(TAG, "Gained audio focus...")
        if (playbackDelayed || resumeOnFocusGain) {
          Log.d(TAG, "Resume playback after audio focus gain...")
          playbackDelayed = false
          resumeOnFocusGain = false
          mSoundManager.resumePlayback()
        }
      }
      AudioManager.AUDIOFOCUS_LOSS -> {
        Log.d(TAG, "Permanently lost audio focus! Pause playback...")
        resumeOnFocusGain = false
        playbackDelayed = false
        mSoundManager.pausePlayback()
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        Log.d(TAG, "Temporarily lost audio focus! Pause playback...")
        resumeOnFocusGain = true
        playbackDelayed = false
        mSoundManager.pausePlayback()
      }
    }
  }

  private fun createAudioFocusRequest(): Int {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      @Suppress("DEPRECATION")
      return mAudioManager.requestAudioFocus(
        this,
        AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
      )
    } else {
      mAudioFocusRequest ?: return AudioManager.AUDIOFOCUS_REQUEST_FAILED
      return mAudioManager.requestAudioFocus(mAudioFocusRequest)
    }
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  fun handleAudioFocusRequestResult(result: Int) {
    Log.d(TAG, "AudioFocusRequest result: $result")
    when (result) {
      AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
        Log.d(TAG, "Audio focus request was delayed! Pause playback for now.")
        playbackDelayed = true
        mSoundManager.pausePlayback()
      }
      AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
        Log.d(TAG, "Failed to get audio focus! Stop playback...")
        mSoundManager.stopPlayback()
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

      val notificationManager = getSystemService(NotificationManager::class.java)
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

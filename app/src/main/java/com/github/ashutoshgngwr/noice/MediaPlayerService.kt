package com.github.ashutoshgngwr.noice

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.github.ashutoshgngwr.noice.sound.PlaybackManager
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MediaPlayerService : Service() {

  companion object {
    const val TAG = "MediaPlayerService"

    const val FOREGROUND_ID = 0x29
    const val RC_MAIN_ACTIVITY = 0x28
    const val RC_START_PLAYBACK = 0x27
    const val RC_STOP_PLAYBACK = 0x26
    const val RC_STOP_SERVICE = 0x25

    const val NOTIFICATION_CHANNEL_ID = "com.github.ashutoshgngwr.noice.default"
  }

  private lateinit var playbackManager: PlaybackManager
  private val becomingNoisyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && playbackManager.isPlaying()) {
        Log.i(TAG, "Becoming noisy... Pause playback!")
        playbackManager.pauseAll()
      }
    }
  }

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    playbackManager = PlaybackManager(this)

    // register becoming noisy receiver to detect audio output config changes
    registerReceiver(
      becomingNoisyReceiver,
      IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    )

    // subscribe to Playback events
    EventBus.getDefault().register(this)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.getIntExtra("action", 0)) {
      RC_START_PLAYBACK -> {
        playbackManager.resumeAll()
      }

      RC_STOP_PLAYBACK -> {
        playbackManager.pauseAll()
      }

      RC_STOP_SERVICE -> {
        playbackManager.stopAll()
      }
    }

    return START_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()

    // unregister receiver and listener. release sound pool resources
    unregisterReceiver(becomingNoisyReceiver)

    // unsubscribe to Playback events
    EventBus.getDefault().unregister(this)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onPlaybackUpdate(ignored: PlaybackManager.UpdateEvent) {
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

    if (playbackManager.isPaused()) {
      notificationBuilder.addAction(
        R.drawable.ic_stat_play,
        getString(R.string.play),
        createPlaybackControlPendingIntent(RC_START_PLAYBACK)
      )
    } else {
      notificationBuilder.addAction(
        R.drawable.ic_stat_pause,
        getString(R.string.pause),
        createPlaybackControlPendingIntent(RC_STOP_PLAYBACK)
      )
    }

    // isPlaying returns true if Playback is paused but not stopped.
    if (!playbackManager.isPlaying()) {
      stopForeground(true)
    } else {
      startForeground(FOREGROUND_ID, notificationBuilder.build())
      if (playbackManager.isPaused()) {
        stopForeground(false)
      }
    }
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
      (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        .createNotificationChannel(channel)
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

package com.github.ashutoshgngwr.noice

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.github.ashutoshgngwr.noice.fragment.SleepTimerFragment
import com.github.ashutoshgngwr.noice.sound.PlaybackControlEvents
import com.github.ashutoshgngwr.noice.sound.PlaybackManager
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MediaPlayerService : Service() {

  companion object {
    private val TAG = MediaPlayerService::class.java.simpleName

    const val FOREGROUND_ID = 0x29
    const val RC_MAIN_ACTIVITY = 0x28
    const val RC_START_PLAYBACK = 0x27
    const val RC_PAUSE_PLAYBACK = 0x26
    const val RC_STOP_PLAYBACK = 0x25

    const val NOTIFICATION_CHANNEL_ID = "com.github.ashutoshgngwr.noice.default"
  }

  private lateinit var playbackManager: PlaybackManager
  private lateinit var handler: Handler // needed in scheduleAutoStop for register callback
  private val autoStopCallback = Runnable {
    EventBus.getDefault().post(PlaybackControlEvents.StopPlaybackEvent())
  }

  private val becomingNoisyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
        Log.i(TAG, "Becoming noisy... Pause playback!")
        EventBus.getDefault().post(PlaybackControlEvents.PausePlaybackEvent())
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
    handler = Handler()

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
        EventBus.getDefault().post(PlaybackControlEvents.StartPlaybackEvent())
      }

      RC_PAUSE_PLAYBACK -> {
        EventBus.getDefault().post(PlaybackControlEvents.PausePlaybackEvent())
      }

      RC_STOP_PLAYBACK -> {
        EventBus.getDefault().post(PlaybackControlEvents.StopPlaybackEvent())
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

    // unregister auto stop callbacks, if any
    handler.removeCallbacks(autoStopCallback)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onPlaybackUpdate(event: PlaybackManager.UpdateEvent) {
    val notificationBuilder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
      .setContentTitle(getString(R.string.app_name))
      .setShowWhen(false)
      .setSmallIcon(R.drawable.ic_launcher_24dp)
      .setContentIntent(
        PendingIntent.getActivity(
          this, RC_MAIN_ACTIVITY,
          Intent(this, MainActivity::class.java),
          PendingIntent.FLAG_UPDATE_CURRENT
        )
      )
      .addAction(
        R.drawable.ic_noti_close,
        getString(R.string.stop),
        createPlaybackControlPendingIntent(RC_STOP_PLAYBACK)
      )

    if (event.state == PlaybackManager.State.PAUSED) {
      notificationBuilder.addAction(
        R.drawable.ic_noti_play,
        getString(R.string.play),
        createPlaybackControlPendingIntent(RC_START_PLAYBACK)
      )
    } else {
      notificationBuilder.addAction(
        R.drawable.ic_noti_pause,
        getString(R.string.pause),
        createPlaybackControlPendingIntent(RC_PAUSE_PLAYBACK)
      )
    }

    // isPlaying returns true if Playback is paused but not stopped.
    if (event.state == PlaybackManager.State.STOPPED) {
      stopForeground(true)
    } else {
      startForeground(FOREGROUND_ID, notificationBuilder.build())
    }
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun scheduleAutoStop(event: SleepTimerFragment.ScheduleAutoSleepEvent) {
    handler.removeCallbacks(autoStopCallback)
    if (event.atUptimeMillis > SystemClock.uptimeMillis()) {
      handler.postAtTime(autoStopCallback, event.atUptimeMillis)
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

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
import com.github.ashutoshgngwr.noice.sound.Playback
import com.github.ashutoshgngwr.noice.sound.PlaybackManager
import com.github.ashutoshgngwr.noice.sound.Sound
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MediaPlayerService : Service() {

  /**
   * [MediaPlayerService] subscribes to these events for starting new playbacks.
   */
  data class StartPlaybackEvent(val soundKey: String)

  /**
   * [MediaPlayerService] subscribes to these events for resuming all paused playbacks.
   */
  class ResumePlaybackEvent

  /**
   * [MediaPlayerService] subscribes to these events for pausing all playbacks.
   */
  class PausePlaybackEvent

  /**
   * [MediaPlayerService] subscribes to these events for stopping playbacks.
   */
  data class StopPlaybackEvent(val soundKey: String?) {
    constructor() : this(null)
  }

  /**
   * [MediaPlayerService] subscribes to these events for updating properties of an ongoing playback.
   * @param soundKey for identifying a playback
   * @param volume will set the playback volume to this
   * @param timePeriod will set the playback timePeriod to this
   */
  data class UpdatePlaybackPropertiesEvent(
    val soundKey: String,
    val volume: Int,
    val timePeriod: Int
  )

  /**
   * [MediaPlayerService] publishes OnPlaybackManagerUpdateEvent whenever there's an update to
   * [PlaybackManager].
   */
  data class OnPlaybackManagerUpdateEvent(
    val state: PlaybackManager.State,
    val playbacks: HashMap<String, Playback>
  )

  /**
   * [MediaPlayerService] subscribes to these events for auto stopping at given time
   * @param atUptimeMillis for stopping playback at given time since boot. If the value is set in
   * the past, the event will cancel the previously scheduled auto sleep timer.
   * See [SystemClock.uptimeMillis].
   */
  data class ScheduleAutoSleepEvent(val atUptimeMillis: Long)

  companion object {
    private val TAG = MediaPlayerService::class.java.simpleName

    const val FOREGROUND_ID = 0x29
    const val RC_MAIN_ACTIVITY = 0x28
    const val RC_START_PLAYBACK = 0x27
    const val RC_PAUSE_PLAYBACK = 0x26
    const val RC_STOP_PLAYBACK = 0x25

    const val NOTIFICATION_CHANNEL_ID = "com.github.ashutoshgngwr.noice.default"
  }

  private val playbackManager by lazy { PlaybackManager(this) }
  private val handler = Handler() // needed in scheduleAutoStop for register callback
  private val eventBus = EventBus.getDefault()
  private val autoStopCallback = Runnable {
    playbackManager.stop()
  }

  private val becomingNoisyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
        Log.i(TAG, "Becoming noisy... Pause playback!")
        playbackManager.pause()
      }
    }
  }

  private val onPlaybackUpdateListener = {
    eventBus.post(OnPlaybackManagerUpdateEvent(playbackManager.state, playbackManager.playbacks))

    // isPlaying returns true if Playback is paused but not stopped.
    if (playbackManager.state == PlaybackManager.State.STOPPED) {
      stopForeground(true)
    } else {
      startForeground(FOREGROUND_ID, createNotification())
    }
  }

  private fun createNotification(): Notification {
    return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).let {
      it.setContentTitle(getString(R.string.app_name))
      it.setShowWhen(false)
      it.setSmallIcon(R.drawable.ic_stat_media_player)
      it.setContentIntent(
        PendingIntent.getActivity(
          this, RC_MAIN_ACTIVITY,
          Intent(this, MainActivity::class.java),
          PendingIntent.FLAG_UPDATE_CURRENT
        )
      )
      it.addAction(
        R.drawable.ic_stat_close,
        getString(R.string.stop),
        createPlaybackControlPendingIntent(RC_STOP_PLAYBACK)
      )

      if (playbackManager.state == PlaybackManager.State.PAUSED) {
        it.addAction(
          R.drawable.ic_stat_play,
          getString(R.string.play),
          createPlaybackControlPendingIntent(RC_START_PLAYBACK)
        )
      } else {
        it.addAction(
          R.drawable.ic_stat_pause,
          getString(R.string.pause),
          createPlaybackControlPendingIntent(RC_PAUSE_PLAYBACK)
        )
      }

      it.build()
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

  override fun onBind(intent: Intent?): IBinder? {
    return null
  }

  override fun onCreate() {
    super.onCreate()
    playbackManager.setOnPlaybackUpdateListener(onPlaybackUpdateListener)
    createNotificationChannel()

    // register becoming noisy receiver to detect audio output config changes
    registerReceiver(
      becomingNoisyReceiver,
      IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    )

    // subscribe to events
    eventBus.register(this)
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

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.getIntExtra("action", 0)) {
      RC_START_PLAYBACK -> {
        playbackManager.resume()
      }

      RC_PAUSE_PLAYBACK -> {
        playbackManager.pause()
      }

      RC_STOP_PLAYBACK -> {
        playbackManager.stop()
      }
    }

    return START_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
    playbackManager.cleanup()

    // unregister receiver and listener. release sound pool resources
    unregisterReceiver(becomingNoisyReceiver)

    // unsubscribe to events
    eventBus.unregister(this)

    // unregister auto stop callbacks, if any
    handler.removeCallbacks(autoStopCallback)
  }

  /**
   * Subscriber for the [StartPlaybackEvent].
   */
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun startPlayback(event: StartPlaybackEvent) {
    playbackManager.play(Sound.get(event.soundKey))
  }

  /**
   * Subscriber for the [ResumePlaybackEvent].
   */
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun resumePlayback(event: ResumePlaybackEvent) {
    playbackManager.resume()
  }

  /**
   * Subscriber for the [PausePlaybackEvent].
   */
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun pausePlayback(event: PausePlaybackEvent) {
    playbackManager.pause()
  }

  /**
   * Subscriber for the [StopPlaybackEvent].
   */
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun stopPlayback(event: StopPlaybackEvent) {
    if (event.soundKey == null) {
      playbackManager.stop()
    } else {
      playbackManager.stop(Sound.get(event.soundKey))
    }
  }

  /**
   * Subscriber for the [UpdatePlaybackPropertiesEvent].
   */
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun updatePlaybackProperties(event: UpdatePlaybackPropertiesEvent) {
    playbackManager.setVolume(event.soundKey, event.volume)
    playbackManager.setTimePeriod(event.soundKey, event.timePeriod)
  }

  /**
   * Subscriber for [ScheduleAutoSleepEvent]
   */
  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun scheduleAutoStop(event: ScheduleAutoSleepEvent) {
    handler.removeCallbacks(autoStopCallback)
    if (event.atUptimeMillis > SystemClock.uptimeMillis()) {
      handler.postAtTime(autoStopCallback, event.atUptimeMillis)
    }
  }
}

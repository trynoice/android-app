package com.github.ashutoshgngwr.noice

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.*
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import com.github.ashutoshgngwr.noice.sound.Sound
import com.github.ashutoshgngwr.noice.sound.player.Player
import com.github.ashutoshgngwr.noice.sound.player.PlayerManager
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.concurrent.TimeUnit

class MediaPlayerService : Service() {

  /**
   * [MediaPlayerService] subscribes to these events for starting new players.
   */
  data class StartPlayerEvent(val soundKey: String, val volume: Int?, val timePeriod: Int?) {
    constructor(soundKey: String) : this(soundKey, null, null)
  }

  /**
   * [MediaPlayerService] subscribes to these events for stopping players.
   */
  data class StopPlayerEvent(val soundKey: String)

  /**
   * [MediaPlayerService] subscribes to these events for resuming all paused players.
   */
  class ResumePlaybackEvent

  /**
   * [MediaPlayerService] subscribes to these events for pausing all players.
   */
  class PausePlaybackEvent

  /**
   * [MediaPlayerService] subscribes to these events for stopping all players.
   */
  class StopPlaybackEvent


  /**
   * [MediaPlayerService] publishes [OnPlayerManagerUpdateEvent] whenever there's an update to
   * [PlayerManager].
   */
  data class OnPlayerManagerUpdateEvent(
    val state: PlayerManager.State,
    val players: Map<String, Player>
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

    private const val NOTIFICATION_CHANNEL_ID = "com.github.ashutoshgngwr.noice.default"
    private const val FOREGROUND_ID = 0x29
    private const val RC_MAIN_ACTIVITY = 0x28
    private val WAKELOCK_TIMEOUT = TimeUnit.DAYS.toMillis(1)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val RC_START_PLAYBACK = 0x27

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val RC_PAUSE_PLAYBACK = 0x26

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val RC_STOP_PLAYBACK = 0x25
  }

  private val handler = Handler() // needed in scheduleAutoStop for register callback
  private val eventBus = EventBus.getDefault()
  private lateinit var playerManager: PlayerManager
  private lateinit var wakeLock: PowerManager.WakeLock
  private val autoStopCallback = Runnable {
    playerManager.stop()
  }

  private val becomingNoisyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
        Log.i(TAG, "Becoming noisy... Pause playback!")
        playerManager.pause()
      }
    }
  }

  private val onPlayerUpdateListener = {
    eventBus.postSticky(OnPlayerManagerUpdateEvent(playerManager.state, playerManager.players))

    if (playerManager.state == PlayerManager.State.STOPPED) {
      stopForeground(true)
      wakeLock.release()
    } else {
      startForeground(FOREGROUND_ID, createNotification())
      wakeLock.acquire(WAKELOCK_TIMEOUT)
    }
  }

  private fun createNotification(): Notification {
    return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).let {
      it.setContentTitle(getString(R.string.app_name))
      it.setShowWhen(false)
      it.setSmallIcon(R.drawable.ic_launcher_24dp)
      it.setContentIntent(
        PendingIntent.getActivity(
          this, RC_MAIN_ACTIVITY,
          Intent(this, MainActivity::class.java),
          PendingIntent.FLAG_UPDATE_CURRENT
        )
      )
      it.addAction(
        R.drawable.ic_noti_close,
        getString(R.string.stop),
        createPlaybackControlPendingIntent(RC_STOP_PLAYBACK)
      )

      if (playerManager.state == PlayerManager.State.PAUSED) {
        it.addAction(
          R.drawable.ic_noti_play,
          getString(R.string.play),
          createPlaybackControlPendingIntent(RC_START_PLAYBACK)
        )
      } else {
        it.addAction(
          R.drawable.ic_noti_pause,
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
    wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
      newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, packageName).apply {
        setReferenceCounted(false)
      }
    }

    // condition needed because Mockk is not able to mock the object initialization.
    // so in order to mock PlayerManager, we inject it with Mockk and avoid calling
    // onCreate whenever we can.
    if (!this::playerManager.isInitialized) {
      playerManager = PlayerManager(this)
    }

    playerManager.setOnPlayerUpdateListener(onPlayerUpdateListener)
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
        playerManager.resume()
      }

      RC_PAUSE_PLAYBACK -> {
        playerManager.pause()
      }

      RC_STOP_PLAYBACK -> {
        playerManager.stop()
      }
    }

    return START_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
    playerManager.cleanup()

    // unregister receiver and listener. release sound pool resources
    unregisterReceiver(becomingNoisyReceiver)

    // unsubscribe to events
    eventBus.unregister(this)

    // unregister auto stop callbacks, if any
    handler.removeCallbacks(autoStopCallback)

    // ensure wake lock is released
    wakeLock.release()
  }

  /**
   * Subscriber for the [StartPlayerEvent].
   */
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun startPlayer(event: StartPlayerEvent) {
    playerManager.play(Sound.get(event.soundKey))
    if (event.volume != null) {
      playerManager.setVolume(event.soundKey, event.volume)
    }

    if (event.timePeriod != null) {
      playerManager.setTimePeriod(event.soundKey, event.timePeriod)
    }
  }

  /**
   * Subscriber for the [StopPlayerEvent].
   */
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun stopPlayer(event: StopPlayerEvent) {
    playerManager.stop(Sound.get(event.soundKey))
  }

  /**
   * Subscriber for the [ResumePlaybackEvent].
   */
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun resumePlayback(@Suppress("UNUSED_PARAMETER") event: ResumePlaybackEvent) {
    playerManager.resume()
  }

  /**
   * Subscriber for the [PausePlaybackEvent].
   */
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun pausePlayback(@Suppress("UNUSED_PARAMETER") event: PausePlaybackEvent) {
    playerManager.pause()
  }

  /**
   * Subscriber for the [StopPlaybackEvent].
   */
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun stopPlayback(@Suppress("UNUSED_PARAMETER") event: StopPlaybackEvent) {
    playerManager.stop()
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

package com.github.ashutoshgngwr.noice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.sound.Preset
import com.github.ashutoshgngwr.noice.sound.Sound
import com.github.ashutoshgngwr.noice.sound.player.Player
import com.github.ashutoshgngwr.noice.sound.player.PlayerManager
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.TimeUnit
import kotlin.math.max

class MediaPlayerService : Service() {
  /**
   * [MediaPlayerService] publishes [OnPlayerManagerUpdateEvent] whenever there's an update to
   * [PlayerManager].
   */
  data class OnPlayerManagerUpdateEvent(
    val state: PlayerManager.State,
    val players: Map<String, Player>
  )

  companion object {
    private val TAG = MediaPlayerService::class.java.simpleName

    private const val NOTIFICATION_CHANNEL_ID = "com.github.ashutoshgngwr.noice.default"
    private const val FOREGROUND_ID = 0x29
    private const val RC_MAIN_ACTIVITY = 0x28
    private val WAKELOCK_TIMEOUT = TimeUnit.DAYS.toMillis(1)

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val ACTION_RESUME_PLAYBACK = "start_playback"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val ACTION_PAUSE_PLAYBACK = "pause_playback"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val ACTION_STOP_PLAYBACK = "stop_playback"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val ACTION_PLAY_SOUND = "play_sound"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val ACTION_STOP_SOUND = "stop_sound"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val EXTRA_SOUND_KEY = "sound_key"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val ACTION_PLAY_PRESET = "play_preset"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val EXTRA_PRESET_ID = "preset_id"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val EXTRA_DEVICE_MEDIA_VOLUME = "device_media_volume"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val ACTION_PLAY_RANDOM_PRESET = "play_random_preset"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val EXTRA_FILTER_SOUNDS_BY_TAG = "filter_sounds_by_tag"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val EXTRA_RANDOM_PRESET_MIN_SOUNDS = "preset_intensity_lower"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val EXTRA_RANDOM_PRESET_MAX_SOUNDS = "preset_intensity_upper"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val ACTION_SCHEDULE_STOP_PLAYBACK = "schedule_stop_playback"

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val EXTRA_AT_UPTIME_MILLIS = "at_uptime_millis"

    private val AUTO_STOP_CALLBACK_TOKEN = "${MediaPlayerService::class.simpleName}.auto_stop_cb"

    /**
     * Sends the start command to the service with [ACTION_PLAY_SOUND].
     */
    fun playSound(context: Context, soundKey: String) {
      context.startService(
        Intent(context, MediaPlayerService::class.java)
          .setAction(ACTION_PLAY_SOUND)
          .putExtra(EXTRA_SOUND_KEY, soundKey)
      )
    }

    /**
     * Sends the start command to the service with [ACTION_STOP_SOUND].
     */
    fun stopSound(context: Context, soundKey: String) {
      context.startService(
        Intent(context, MediaPlayerService::class.java)
          .setAction(ACTION_STOP_SOUND)
          .putExtra(EXTRA_SOUND_KEY, soundKey)
      )
    }

    /**
     * Sends the start command to the service with [ACTION_PAUSE_PLAYBACK].
     */
    fun pausePlayback(context: Context) {
      context.startService(
        Intent(context, MediaPlayerService::class.java)
          .setAction(ACTION_PAUSE_PLAYBACK)
      )
    }

    /**
     * Sends the start command to the service with [ACTION_RESUME_PLAYBACK].
     */
    fun resumePlayback(context: Context) {
      context.startService(
        Intent(context, MediaPlayerService::class.java)
          .setAction(ACTION_RESUME_PLAYBACK)
      )
    }

    /**
     * Sends the start command to the service with [ACTION_STOP_PLAYBACK].
     */
    fun stopPlayback(context: Context) {
      context.startService(
        Intent(context, MediaPlayerService::class.java)
          .setAction(ACTION_STOP_PLAYBACK)
      )
    }

    /**
     * Sends the start command to the service with [ACTION_PLAY_PRESET].
     */
    fun playPreset(context: Context, presetID: String?) {
      context.startService(
        Intent(context, MediaPlayerService::class.java)
          .setAction(ACTION_PLAY_PRESET)
          .putExtra(EXTRA_PRESET_ID, presetID)
      )
    }

    /**
     * Sends the start command to the service with [ACTION_PLAY_RANDOM_PRESET].
     */
    fun playRandomPreset(context: Context, tag: Sound.Tag?, intensity: IntRange) {
      context.startService(
        Intent(context, MediaPlayerService::class.java)
          .setAction(ACTION_PLAY_RANDOM_PRESET)
          .putExtra(EXTRA_FILTER_SOUNDS_BY_TAG, tag)
          .putExtra(EXTRA_RANDOM_PRESET_MIN_SOUNDS, intensity.first)
          .putExtra(EXTRA_RANDOM_PRESET_MAX_SOUNDS, intensity.last)
      )
    }

    private var lastScheduledAutoStopTime = 0L

    /**
     * Sends the start command to the service with [ACTION_SCHEDULE_STOP_PLAYBACK].
     */
    fun scheduleStopPlayback(context: Context, afterDurationMillis: Long) {
      lastScheduledAutoStopTime = SystemClock.uptimeMillis() + afterDurationMillis
      context.startService(
        Intent(context, MediaPlayerService::class.java)
          .setAction(ACTION_SCHEDULE_STOP_PLAYBACK)
          .putExtra(EXTRA_AT_UPTIME_MILLIS, lastScheduledAutoStopTime)
      )
    }

    /**
     * Returns the uptime millis for the last stop playback schedule.
     */
    fun getScheduledStopPlaybackRemainingDurationMillis() =
      max(lastScheduledAutoStopTime - SystemClock.uptimeMillis(), 0)
  }

  private val handler = Handler(Looper.getMainLooper())

  private lateinit var playerManager: PlayerManager
  private lateinit var wakeLock: PowerManager.WakeLock
  private lateinit var presetRepository: PresetRepository

  private val becomingNoisyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
        Log.i(TAG, "Becoming noisy... Pause playback!")
        playerManager.pause()
      }
    }
  }

  private val onPlayerUpdateListener = {
    EventBus.getDefault()
      .postSticky(OnPlayerManagerUpdateEvent(playerManager.state, playerManager.players))

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
        createPlaybackControlPendingIntent(0x12, ACTION_STOP_PLAYBACK)
      )

      if (playerManager.state == PlayerManager.State.PAUSED) {
        it.addAction(
          R.drawable.ic_noti_play,
          getString(R.string.play),
          createPlaybackControlPendingIntent(0x13, ACTION_RESUME_PLAYBACK)
        )
      } else {
        it.addAction(
          R.drawable.ic_noti_pause,
          getString(R.string.pause),
          createPlaybackControlPendingIntent(0x14, ACTION_PAUSE_PLAYBACK)
        )
      }

      it.build()
    }
  }

  private fun createPlaybackControlPendingIntent(requestCode: Int, action: String): PendingIntent {
    return PendingIntent.getService(
      this,
      requestCode,
      Intent(this, MediaPlayerService::class.java).also { it.action = action },
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

    if (!this::presetRepository.isInitialized) {
      presetRepository = PresetRepository.newInstance(this)
    }

    playerManager.setOnPlayerUpdateListener(onPlayerUpdateListener)
    createNotificationChannel()

    // register becoming noisy receiver to detect audio output config changes
    registerReceiver(
      becomingNoisyReceiver,
      IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    )
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
    when (intent?.action) {
      ACTION_RESUME_PLAYBACK -> {
        playerManager.resume()
      }

      ACTION_PAUSE_PLAYBACK -> {
        playerManager.pause()
      }

      ACTION_STOP_PLAYBACK -> {
        playerManager.stop()
      }

      ACTION_PLAY_SOUND -> {
        playerManager.play(getSoundKeyExtra(intent))
      }

      ACTION_STOP_SOUND -> {
        playerManager.stop(getSoundKeyExtra(intent))
      }

      ACTION_PLAY_PRESET -> {
        val mediaVol = intent.getIntExtra(EXTRA_DEVICE_MEDIA_VOLUME, -1)
        if (mediaVol >= 0) {
          requireNotNull(getSystemService<AudioManager>())
            .setStreamVolume(AudioManager.STREAM_MUSIC, mediaVol, 0)
        }

        intent.getStringExtra(EXTRA_PRESET_ID)?.also {
          Log.d(TAG, "starting preset with id: $it")
          presetRepository.get(it)?.also { preset ->
            playerManager.playPreset(preset)
          }
        }
      }

      ACTION_PLAY_RANDOM_PRESET -> {
        val tag = intent.getSerializableExtra(EXTRA_FILTER_SOUNDS_BY_TAG) as Sound.Tag?
        val minSounds = intent.getIntExtra(EXTRA_RANDOM_PRESET_MIN_SOUNDS, 1)
        val maxSounds = intent.getIntExtra(EXTRA_RANDOM_PRESET_MAX_SOUNDS, 0)
        if (minSounds > maxSounds) {
          throw IllegalArgumentException("invalid range for number of sounds in random preset")
        }

        playerManager.playPreset(Preset.random(tag, minSounds..maxSounds))
      }

      ACTION_SCHEDULE_STOP_PLAYBACK -> {
        handler.removeCallbacksAndMessages(AUTO_STOP_CALLBACK_TOKEN)
        val atUptime = intent.getLongExtra(EXTRA_AT_UPTIME_MILLIS, 0)
        if (atUptime > SystemClock.uptimeMillis()) {
          // pause, not stop. give user a chance to resume if they chose to do so.
          handler.postAtTime({ playerManager.pause() }, AUTO_STOP_CALLBACK_TOKEN, atUptime)
        }
      }
    }

    return START_STICKY
  }

  private fun getSoundKeyExtra(intent: Intent): String {
    return intent.getStringExtra(EXTRA_SOUND_KEY)
      ?: throw IllegalArgumentException("'EXTRA_SOUND_KEY' must not be null")
  }

  override fun onDestroy() {
    super.onDestroy()
    playerManager.cleanup()

    // unregister receiver and listener. release sound pool resources
    unregisterReceiver(becomingNoisyReceiver)

    // unregister auto stop callbacks, if any
    handler.removeCallbacksAndMessages(AUTO_STOP_CALLBACK_TOKEN)

    // ensure wake lock is released
    wakeLock.release()
  }
}

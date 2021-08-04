package com.github.ashutoshgngwr.noice.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.media.AudioAttributesCompat
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.model.Sound
import kotlin.math.max

object PlaybackController {

  internal const val ACTION_RESUME_PLAYBACK = "start_playback"
  internal const val ACTION_PAUSE_PLAYBACK = "pause_playback"
  internal const val ACTION_STOP_PLAYBACK = "stop_playback"
  internal const val ACTION_PLAY_SOUND = "play_sound"
  internal const val ACTION_STOP_SOUND = "stop_sound"
  internal const val EXTRA_SOUND_KEY = "sound_key"
  internal const val ACTION_PLAY_PRESET = "play_preset"
  internal const val EXTRA_PRESET_ID = "preset_id"
  internal const val ACTION_PLAY_RANDOM_PRESET = "play_random_preset"
  internal const val EXTRA_FILTER_SOUNDS_BY_TAG = "filter_sounds_by_tag"
  internal const val EXTRA_RANDOM_PRESET_MIN_SOUNDS = "preset_intensity_lower"
  internal const val EXTRA_RANDOM_PRESET_MAX_SOUNDS = "preset_intensity_upper"
  internal const val ACTION_SCHEDULE_STOP_PLAYBACK = "schedule_stop_playback"
  internal const val EXTRA_AT_UPTIME_MILLIS = "at_uptime_millis"
  internal const val ACTION_SKIP_PRESET = "skip_preset"
  internal const val EXTRA_SKIP_DIRECTION = "skip_direction"
  internal const val ACTION_REQUEST_UPDATE_EVENT = "request_update_event"
  internal const val ACTION_SET_AUDIO_USAGE = "set_audio_usage"
  internal const val EXTRA_AUDIO_USAGE = "audio_usage"

  private val TAG = PlaybackController::class.simpleName
  private val AUTO_STOP_CALLBACK_TOKEN = "${TAG}.auto_stop_cb"

  internal val PREF_LAST_SCHEDULED_STOP_TIME = "${TAG}.scheduled_stop_time"

  private const val RC_SKIP_PREV = 0x3A
  private const val RC_SKIP_NEXT = 0x3B
  private const val RC_RESUME = 0x3C
  private const val RC_PAUSE = 0x3D
  private const val RC_STOP = 0x3E

  private val handler = Handler(Looper.getMainLooper())

  fun buildResumeActionPendingIntent(context: Context): PendingIntent {
    return buildSimpleActionPendingIntent(context, ACTION_RESUME_PLAYBACK, RC_RESUME)
  }

  fun buildPauseActionPendingIntent(context: Context): PendingIntent {
    return buildSimpleActionPendingIntent(context, ACTION_PAUSE_PLAYBACK, RC_PAUSE)
  }

  fun buildStopActionPendingIntent(context: Context): PendingIntent {
    return buildSimpleActionPendingIntent(context, ACTION_STOP_PLAYBACK, RC_STOP)
  }

  private fun buildSimpleActionPendingIntent(
    context: Context,
    action: String,
    requestCode: Int
  ): PendingIntent {
    val intent = Intent(context, MediaPlayerService::class.java)
      .setAction(action)

    return buildPendingIntent(context, intent, requestCode)
  }

  fun buildSkipPrevActionPendingIntent(context: Context): PendingIntent {
    return buildSkipActionPendingIntent(context, PlayerManager.SKIP_DIRECTION_PREV, RC_SKIP_PREV)
  }

  fun buildSkipNextActionPendingIntent(context: Context): PendingIntent {
    return buildSkipActionPendingIntent(context, PlayerManager.SKIP_DIRECTION_NEXT, RC_SKIP_NEXT)
  }

  private fun buildSkipActionPendingIntent(
    context: Context,
    direction: Int,
    requestCode: Int
  ): PendingIntent {
    val intent = Intent(context, MediaPlayerService::class.java)
      .setAction(ACTION_SKIP_PRESET)
      .putExtra(EXTRA_SKIP_DIRECTION, direction)

    return buildPendingIntent(context, intent, requestCode)
  }

  private fun buildPendingIntent(
    context: Context,
    intent: Intent,
    requestCode: Int
  ): PendingIntent {
    var piFlags = PendingIntent.FLAG_UPDATE_CURRENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      piFlags = piFlags or PendingIntent.FLAG_IMMUTABLE
    }

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      PendingIntent.getForegroundService(context, requestCode, intent, piFlags)
    } else {
      PendingIntent.getService(context, requestCode, intent, piFlags)
    }
  }

  fun handleServiceIntent(context: Context, playerManager: PlayerManager, intent: Intent) {
    when (intent.action) {
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
        intent.getStringExtra(EXTRA_PRESET_ID)?.also { playerManager.playPreset(it) }
        intent.data?.also { playerManager.playPreset(it) }
        logEvent(context, "preset_playback", bundleOf("items_count" to playerManager.playerCount()))
      }

      ACTION_PLAY_RANDOM_PRESET -> {
        val tag = intent.getSerializableExtra(EXTRA_FILTER_SOUNDS_BY_TAG) as Sound.Tag?
        val minSounds = intent.getIntExtra(EXTRA_RANDOM_PRESET_MIN_SOUNDS, 1)
        val maxSounds = intent.getIntExtra(EXTRA_RANDOM_PRESET_MAX_SOUNDS, 0)
        if (minSounds > maxSounds) {
          throw IllegalArgumentException("invalid range for number of sounds in random preset")
        }

        playerManager.playRandomPreset(tag, minSounds..maxSounds)
        val params = bundleOf(
          "min_items_count" to minSounds,
          "max_items_count" to maxSounds,
          "items_count" to playerManager.playerCount(),
          "sound_tag" to (tag?.name ?: "NONE"),
        )

        logEvent(context, "random_preset_playback", params)
      }

      ACTION_SCHEDULE_STOP_PLAYBACK -> {
        handler.removeCallbacksAndMessages(AUTO_STOP_CALLBACK_TOKEN)
        val atUptime = intent.getLongExtra(EXTRA_AT_UPTIME_MILLIS, 0)
        if (atUptime > SystemClock.uptimeMillis()) {
          // pause, not stop. give user a chance to resume if they chose to do so.
          handler.postAtTime({ playerManager.pause() }, AUTO_STOP_CALLBACK_TOKEN, atUptime)
        }
      }

      ACTION_SKIP_PRESET -> {
        val skipDirection = intent.getIntExtra(EXTRA_SKIP_DIRECTION, 1)
        playerManager.skipPreset(skipDirection)
        logEvent(context, "preset_skip", bundleOf("skip_direction" to skipDirection))
      }

      ACTION_REQUEST_UPDATE_EVENT -> {
        playerManager.callPlaybackUpdateListener()
      }

      ACTION_SET_AUDIO_USAGE -> {
        val audioUsage = intent.getIntExtra(EXTRA_AUDIO_USAGE, AudioAttributesCompat.USAGE_MEDIA)
        playerManager.setAudioUsage(audioUsage)
      }
    }
  }

  private fun getSoundKeyExtra(intent: Intent): String {
    return intent.getStringExtra(EXTRA_SOUND_KEY)
      ?: throw IllegalArgumentException("'EXTRA_SOUND_KEY' must not be null")
  }

  private fun logEvent(context: Context, name: String, params: Bundle) {
    NoiceApplication.of(context)
      .getAnalyticsProvider()
      .logEvent(name, params)
  }

  /**
   * Sends the start command to the service with [ACTION_PLAY_SOUND].
   */
  fun play(context: Context, soundKey: String) {
    context.startService(
      Intent(context, MediaPlayerService::class.java)
        .setAction(ACTION_PLAY_SOUND)
        .putExtra(EXTRA_SOUND_KEY, soundKey)
    )
  }

  /**
   * Sends the start command to the service with [ACTION_STOP_SOUND].
   */
  fun stop(context: Context, soundKey: String) {
    context.startService(
      Intent(context, MediaPlayerService::class.java)
        .setAction(ACTION_STOP_SOUND)
        .putExtra(EXTRA_SOUND_KEY, soundKey)
    )
  }

  /**
   * Sends the start command to the service with [ACTION_PAUSE_PLAYBACK].
   */
  fun pause(context: Context) {
    context.startService(
      Intent(context, MediaPlayerService::class.java)
        .setAction(ACTION_PAUSE_PLAYBACK)
    )
  }

  /**
   * Sends the start command to the service with [ACTION_RESUME_PLAYBACK].
   */
  fun resume(context: Context) {
    context.startService(
      Intent(context, MediaPlayerService::class.java)
        .setAction(ACTION_RESUME_PLAYBACK)
    )
  }

  /**
   * Sends the start command to the service with [ACTION_STOP_PLAYBACK].
   */
  fun stop(context: Context) {
    context.startService(
      Intent(context, MediaPlayerService::class.java)
        .setAction(ACTION_STOP_PLAYBACK)
    )
  }

  /**
   * Sends the start command to the service with [ACTION_PLAY_PRESET].
   */
  fun playPreset(context: Context, presetID: String) {
    context.startService(
      Intent(context, MediaPlayerService::class.java)
        .setAction(ACTION_PLAY_PRESET)
        .putExtra(EXTRA_PRESET_ID, presetID)
    )
  }

  /**
   * Sends the start command to the service with [ACTION_PLAY_PRESET] with [uri] as its data.
   */
  fun playPresetFromUri(context: Context, uri: Uri) {
    context.startService(
      Intent(context, MediaPlayerService::class.java)
        .setAction(ACTION_PLAY_PRESET)
        .setData(uri)
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

  /**
   * Removes the scheduled callback for auto stopping playback.
   */
  fun clearScheduledAutoStop(context: Context) {
    scheduleAutoStop(context, -1)
  }

  /**
   * Sends the start command to the service with [ACTION_SCHEDULE_STOP_PLAYBACK].
   */
  fun scheduleAutoStop(context: Context, afterDurationMillis: Long) {
    val atUptimeMillis = SystemClock.uptimeMillis() + afterDurationMillis
    PreferenceManager.getDefaultSharedPreferences(context)
      .edit(commit = true) { putLong(PREF_LAST_SCHEDULED_STOP_TIME, atUptimeMillis) }

    context.startService(
      Intent(context, MediaPlayerService::class.java)
        .setAction(ACTION_SCHEDULE_STOP_PLAYBACK)
        .putExtra(EXTRA_AT_UPTIME_MILLIS, atUptimeMillis)
    )
  }

  /**
   * Returns the uptime millis for the last stop playback schedule.
   */
  fun getScheduledAutoStopRemainingDurationMillis(context: Context): Long {
    val atUptimeMillis = PreferenceManager.getDefaultSharedPreferences(context)
      .getLong(PREF_LAST_SCHEDULED_STOP_TIME, 0)

    return max(atUptimeMillis - SystemClock.uptimeMillis(), 0)
  }

  fun clearAutoStopCallback() {
    handler.removeCallbacksAndMessages(AUTO_STOP_CALLBACK_TOKEN)
  }

  fun requestUpdateEvent(context: Context) {
    context.startService(
      Intent(context, MediaPlayerService::class.java)
        .setAction(ACTION_REQUEST_UPDATE_EVENT)
    )
  }

  fun setAudioUsage(context: Context, @AudioAttributesCompat.AttributeUsage usage: Int) {
    context.startService(
      Intent(context, MediaPlayerService::class.java)
        .setAction(ACTION_SET_AUDIO_USAGE)
        .putExtra(EXTRA_AUDIO_USAGE, usage)
    )
  }
}

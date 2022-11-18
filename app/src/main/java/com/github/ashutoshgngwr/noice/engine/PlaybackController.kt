package com.github.ashutoshgngwr.noice.engine

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.media.AudioAttributesCompat
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.ext.bindServiceCallbackFlow
import com.github.ashutoshgngwr.noice.model.PlayerState
import com.github.ashutoshgngwr.noice.model.Preset
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * A convenience class that provides the controls of [PlaybackService] via start-service intents.
 */
@Singleton
class PlaybackController @Inject constructor(@ApplicationContext private val context: Context) {

  private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

  /**
   * Sends the start command to the service with [PlaybackService.ACTION_PLAY_SOUND].
   */
  fun play(soundId: String) {
    commandPlaybackService(true) {
      action = PlaybackService.ACTION_PLAY_SOUND
      putExtra(PlaybackService.INTENT_EXTRA_SOUND_ID, soundId)
    }
  }

  /**
   * Sends the start command to the service with [PlaybackService.ACTION_STOP_SOUND].
   */
  fun stop(soundId: String) {
    commandPlaybackService(false) {
      action = PlaybackService.ACTION_STOP_SOUND
      putExtra(PlaybackService.INTENT_EXTRA_SOUND_ID, soundId)
    }
  }

  fun setMasterVolume(volume: Int) {
    commandPlaybackService(false) {
      action = PlaybackService.ACTION_SET_MASTER_VOLUME
      putExtra(PlaybackService.INTENT_EXTRA_VOLUME, volume)
    }
  }

  fun setVolume(soundId: String, volume: Int) {
    commandPlaybackService(false) {
      action = PlaybackService.ACTION_SET_SOUND_VOLUME
      putExtra(PlaybackService.INTENT_EXTRA_SOUND_ID, soundId)
      putExtra(PlaybackService.INTENT_EXTRA_VOLUME, volume)
    }
  }

  /**
   * Sends the start command to the service with [PlaybackService.ACTION_PAUSE].
   */
  fun pause(skipFadeTransition: Boolean = false) {
    commandPlaybackService(false) {
      action = PlaybackService.ACTION_PAUSE
      putExtra(PlaybackService.INTENT_EXTRA_SKIP_FADE_TRANSITION, skipFadeTransition)
    }
  }

  /**
   * Sends the start command to the service with [PlaybackService.ACTION_RESUME].
   */
  fun resume() {
    commandPlaybackService(true) { action = PlaybackService.ACTION_RESUME }
  }

  /**
   * Sends the start command to the service with [PlaybackService.ACTION_STOP].
   */
  fun stop() {
    commandPlaybackService(false) { action = PlaybackService.ACTION_STOP }
  }

  /**
   * Sends the start command to the service with [PlaybackService.ACTION_PLAY_PRESET] with the given
   * [preset].
   */
  fun play(preset: Preset) {
    commandPlaybackService(true) {
      action = PlaybackService.ACTION_PLAY_PRESET
      putExtra(PlaybackService.INTENT_EXTRA_PRESET, preset)
    }
  }

  /**
   * Sends the start command to the service with [PlaybackService.ACTION_SCHEDULE_STOP].
   */
  fun scheduleStop(afterDurationMillis: Long) {
    val atMillis = System.currentTimeMillis() + afterDurationMillis
    prefs.edit(commit = true) { putLong(PREF_SCHEDULED_STOP_MILLIS, atMillis) }
    commandPlaybackService(false) {
      action = PlaybackService.ACTION_SCHEDULE_STOP
      putExtra(PlaybackService.INTENT_EXTRA_SCHEDULED_STOP_AT_MILLIS, atMillis)
    }
  }

  /**
   * Clears the automatic stop schedule for the playback service.
   */
  fun clearScheduledAutoStop() {
    prefs.edit { remove(PREF_SCHEDULED_STOP_MILLIS) }
    commandPlaybackService(false) { action = PlaybackService.ACTION_CLEAR_STOP_SCHEDULE }
  }

  /**
   * Returns the remaining duration millis for the last automatic stop schedule for the playback
   * service.
   */
  fun getStopScheduleRemainingMillis(): Long {
    return max(prefs.getLong(PREF_SCHEDULED_STOP_MILLIS, 0) - System.currentTimeMillis(), 0)
  }

  /**
   * Sets the audio usage ([AudioAttributesCompat.AttributeUsage]) that the playback service will
   * use.
   */
  fun setAudioUsage(@AudioAttributesCompat.AttributeUsage usage: Int) {
    commandPlaybackService(false) {
      action = PlaybackService.ACTION_SET_AUDIO_USAGE
      putExtra(PlaybackService.INTENT_EXTRA_AUDIO_USAGE, usage)
    }
  }

  private inline fun commandPlaybackService(foreground: Boolean, intentBuilder: Intent.() -> Unit) {
    val intent = Intent(context, PlaybackService::class.java)
    intentBuilder.invoke(intent)
    if (foreground) {
      ContextCompat.startForegroundService(context, intent)
    } else {
      context.startService(intent)
    }
  }

  /**
   * @return a [Flow] that emits the Player Manager's current [PlaybackState].
   */
  fun getPlayerManagerState(): Flow<PlaybackState> =
    context.bindServiceCallbackFlow<PlaybackService, PlaybackServiceBinder, PlaybackState> { binder ->
      binder.playerManagerState
    }

  /**
   * @return a [Flow] that emits the Player Manager's current master volume whenever it changes.
   */
  fun getMasterVolume(): Flow<Int> =
    context.bindServiceCallbackFlow<PlaybackService, PlaybackServiceBinder, Int> { binder ->
      binder.masterVolume
    }

  /**
   * @return a [Flow] that emits an array of [PlayerState]s of all players managed by the Player
   * Manager.
   */
  fun getPlayerStates(): Flow<Array<PlayerState>> =
    context.bindServiceCallbackFlow<PlaybackService, PlaybackServiceBinder, Array<PlayerState>> { binder ->
      binder.playerStates
    }

  companion object {
    internal const val DEFAULT_SOUND_VOLUME = Player.DEFAULT_VOLUME
    internal const val MAX_MASTER_VOLUME = PlayerManager.MAX_VOLUME
    internal const val MAX_SOUND_VOLUME = Player.MAX_VOLUME
    private const val PREF_SCHEDULED_STOP_MILLIS = "scheduledStopMillis"

    /**
     * Returns a [PendingIntent] that issues resume command to the [PlaybackService].
     */
    fun buildResumeActionPendingIntent(context: Context): PendingIntent {
      return buildPendingIntent(context, 0x3A, true) { action = PlaybackService.ACTION_RESUME }
    }

    /**
     * Returns a [PendingIntent] that issues pause command to the [PlaybackService].
     */
    fun buildPauseActionPendingIntent(context: Context): PendingIntent {
      return buildPendingIntent(context, 0x3B, false) { action = PlaybackService.ACTION_PAUSE }
    }

    /**
     * Returns a [PendingIntent] that issues stop command to the [PlaybackService].
     */
    fun buildStopActionPendingIntent(context: Context): PendingIntent {
      return buildPendingIntent(context, 0x3C, false) { action = PlaybackService.ACTION_STOP }
    }

    /**
     * Returns a [PendingIntent] that issues a command to the [PlaybackService] to generate and play
     * a random preset.
     */
    fun buildRandomPresetActionPendingIntent(context: Context): PendingIntent {
      return buildPendingIntent(context, 0x3D, true) {
        action = PlaybackService.ACTION_PLAY_RANDOM_PRESET
      }
    }

    /**
     * Returns a [PendingIntent] that issues a command to the [PlaybackService] to play the
     * previous preset.
     */
    fun buildSkipPrevActionPendingIntent(context: Context): PendingIntent {
      return buildPendingIntent(context, 0x3E, true) {
        action = PlaybackService.ACTION_SKIP_PRESET
        putExtra(
          PlaybackService.INTENT_EXTRA_PRESET_SKIP_DIRECTION,
          PlaybackService.PRESET_SKIP_DIRECTION_PREV
        )
      }
    }

    /**
     * Returns a [PendingIntent] that issues a command to the [PlaybackService] to play the next
     * preset.
     */
    fun buildSkipNextActionPendingIntent(context: Context): PendingIntent {
      return buildPendingIntent(context, 0x3F, true) {
        action = PlaybackService.ACTION_SKIP_PRESET
        putExtra(
          PlaybackService.INTENT_EXTRA_PRESET_SKIP_DIRECTION,
          PlaybackService.PRESET_SKIP_DIRECTION_NEXT
        )
      }
    }

    private inline fun buildPendingIntent(
      context: Context,
      requestCode: Int,
      foreground: Boolean,
      intentBuilder: Intent.() -> Unit,
    ): PendingIntent {
      val piFlags = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      }

      val intent = Intent(context, PlaybackService::class.java)
      intentBuilder.invoke(intent)
      return if (foreground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        PendingIntent.getForegroundService(context, requestCode, intent, piFlags)
      } else {
        PendingIntent.getService(context, requestCode, intent, piFlags)
      }
    }
  }
}

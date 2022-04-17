package com.github.ashutoshgngwr.noice.playback

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.os.HandlerCompat
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.playback.strategy.PlaybackStrategy
import com.github.ashutoshgngwr.noice.playback.strategy.PlaybackStrategyFactory
import com.trynoice.api.client.models.SoundGroup
import kotlin.math.pow
import kotlin.random.Random.Default.nextInt

/**
 * [Player] manages playback of a single [Sound]. It [PlaybackStrategy] instances to control media
 * playback and keeps track of playback information such as [isPlaying], [volume] and [timePeriod].
 */
class Player(val soundKey: String, playbackStrategyFactory: PlaybackStrategyFactory) {

  companion object {
    private val TAG = Player::class.simpleName

    const val DEFAULT_VOLUME = 4
    const val MAX_VOLUME = 25
    const val DEFAULT_TIME_PERIOD = 300
    const val MIN_TIME_PERIOD = 30
    const val MAX_TIME_PERIOD = 1200
  }

  var volume = DEFAULT_VOLUME
    private set

  var timePeriod = DEFAULT_TIME_PERIOD

  // TODO:
  private val sound = Sound(
    soundKey, SoundGroup("", ""), soundKey, "", 0, "", emptyList(), emptyList()
  )

  private var isPlaying = false
  private var playbackStrategy = playbackStrategyFactory.newInstance(sound).also {
    it.setVolume(getScaledVolume())
  }

  private val handler = Handler(Looper.getMainLooper())

  /**
   * Sets the volume for the [Player] using current [PlaybackStrategy].
   */
  fun setVolume(volume: Int) {
    this.volume = volume
    this.playbackStrategy.setVolume(getScaledVolume())
  }

  private fun getScaledVolume(): Float {
    // return 0.5f * log(max(1, volume).toFloat(), 5f) // logarithmic
    return (0.04f * volume.toFloat()).pow(2) // quadratic
    // return (0.04f * volume.toFloat()).pow(3) // cubic
  }

  /**
   * Starts playing the sound. If the sound is not looping, it also schedules a delayed
   * task to replay the sound. Delay period is randomised with guaranteed
   * [MIN_TIME_PERIOD][MIN_TIME_PERIOD].
   */
  internal fun play() {
    isPlaying = true
    if (sound.isLooping) {
      playbackStrategy.play()
    } else {
      playAndRegisterDelayedCallback()
    }
  }

  /**
   * Implements the randomised play callback for non-looping sounds.
   */
  private fun playAndRegisterDelayedCallback() {
    if (!isPlaying) {
      Log.d(TAG, "delayed callback invoked but not playing! won't perform playback.")
      return
    }

    playbackStrategy.play()
    val delay = nextInt(MIN_TIME_PERIOD, 1 + timePeriod) * 1000L
    Log.d(TAG, "scheduling delayed playback with ${delay}ms delay")
    HandlerCompat.postDelayed(handler, this::playAndRegisterDelayedCallback, this, delay)
  }

  /**
   * Stops the [Player] without releasing the underlying media resource.
   * If the sound is non-looping, it also removes the randomised play callback.
   */
  internal fun pause() {
    isPlaying = false
    playbackStrategy.pause()
    if (!sound.isLooping) {
      handler.removeCallbacksAndMessages(this)
    }
  }

  /**
   * Stops the [Player] and releases the underlying media resource.
   * If the sound is non-looping, it also removes the randomised play callback.
   */
  internal fun stop() {
    isPlaying = false
    playbackStrategy.stop()
    if (!sound.isLooping) {
      handler.removeCallbacksAndMessages(this)
    }
  }

  /**
   * Sets the provided [AudioAttributesCompat] to the underlying [PlaybackStrategy].
   */
  internal fun setAudioAttributes(attrs: AudioAttributesCompat) {
    playbackStrategy.setAudioAttributes(attrs)
  }

  /**
   * [updatePlaybackStrategy] updates the [PlaybackStrategy] used by the [Player] instance. All subsequent
   * player control commands are sent to the new [PlaybackStrategy]. It also sends setVolume command
   * on the new [PlaybackStrategy]. If [Player] is looping and playing, it also sends the play
   * command on the new [PlaybackStrategy].
   */
  internal fun updatePlaybackStrategy(playbackStrategyFactory: PlaybackStrategyFactory) {
    playbackStrategy.stop()
    playbackStrategy = playbackStrategyFactory.newInstance(sound).also {
      it.setVolume(volume.toFloat() / MAX_VOLUME)
      if (isPlaying && sound.isLooping) { // because non looping will automatically play on scheduled callback.
        it.play()
      }
    }
  }
}

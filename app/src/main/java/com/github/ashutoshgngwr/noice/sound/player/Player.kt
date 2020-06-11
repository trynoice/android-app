package com.github.ashutoshgngwr.noice.sound.player

import android.os.Handler
import com.github.ashutoshgngwr.noice.sound.Sound
import com.github.ashutoshgngwr.noice.sound.player.adapter.PlayerAdapter
import com.github.ashutoshgngwr.noice.sound.player.adapter.PlayerAdapterFactory
import kotlin.random.Random.Default.nextInt

/**
 * [Player] manages playback of a single [Sound]. It [PlayerAdapter] instances to control media
 * playback and keeps track of playback information such as [isPlaying], [volume] and [timePeriod].
 */
class Player(private val sound: Sound, playerAdapterFactory: PlayerAdapterFactory) {

  companion object {
    const val DEFAULT_VOLUME = 4
    const val MAX_VOLUME = 20
    const val DEFAULT_TIME_PERIOD = 60
    private const val MIN_TIME_PERIOD = 30
    const val MAX_TIME_PERIOD = 300
  }

  var volume = DEFAULT_VOLUME
    private set

  var timePeriod = DEFAULT_TIME_PERIOD

  val soundKey = sound.key

  private var isPlaying = false
  private var playerAdapter = playerAdapterFactory.newPlayerAdapter(sound).also {
    it.setVolume(volume.toFloat() / MAX_VOLUME)
  }

  private val handler = Handler()

  /**
   * Sets the volume for the [Player] using current [PlayerAdapter].
   */
  fun setVolume(volume: Int) {
    this.volume = volume
    this.playerAdapter.setVolume(volume.toFloat() / MAX_VOLUME)
  }

  /**
   * Starts playing the sound. If the sound is not loopable, it also schedules a delayed
   * task to replay the sound. Delay period is randomised with guaranteed
   * [MIN_TIME_PERIOD][MIN_TIME_PERIOD].
   */
  fun play() {
    isPlaying = true
    if (sound.isLoopable) {
      playerAdapter.play()
    } else {
      playAndRegisterDelayedCallback()
    }
  }

  /**
   * Implements the randomised play callback for non-looping sounds.
   */
  private fun playAndRegisterDelayedCallback() {
    if (!isPlaying) {
      return
    }

    playerAdapter.play()
    val delay = nextInt(MIN_TIME_PERIOD, 1 + timePeriod) * 1000L
    handler.postDelayed(this::playAndRegisterDelayedCallback, delay)
  }

  /**
   * Stops the [Player] without releasing the underlying media resource.
   * If the sound is non-loopable, it also removes the randomised play callback.
   */
  fun pause() {
    isPlaying = false
    playerAdapter.pause()
    if (sound.isLoopable) {
      handler.removeCallbacks(this::playAndRegisterDelayedCallback)
    }
  }

  /**
   * Stops the [Player] and releases the underlying media resource.
   * If the sound is non-loopable, it also removes the randomised play callback.
   */
  fun stop() {
    isPlaying = false
    playerAdapter.stop()
    if (sound.isLoopable) {
      handler.removeCallbacks(this::playAndRegisterDelayedCallback)
    }
  }

  /**
   * setAdapter updates the [PlayerAdapter] used by the [Player] instance. All subsequent
   * player control commands are sent to the new [PlayerAdapter]. It also sends setVolume command
   * on the new [PlayerAdapter]. If [Player] is looping and playing, it also sends the play
   * command on the new [PlayerAdapter].
   */
  fun recreatePlayerAdapter(playerAdapterFactory: PlayerAdapterFactory) {
    // pause then stop just to prevent the fade-out transition from LocalSoundPlayer
    playerAdapter.pause()
    playerAdapter.stop()
    playerAdapter = playerAdapterFactory.newPlayerAdapter(sound).also {
      it.setVolume(volume.toFloat() / MAX_VOLUME)
      if (isPlaying && sound.isLoopable) { // because non looping will automatically play on scheduled callback.
        it.play()
      }
    }
  }
}

package com.github.ashutoshgngwr.noice.engine

import androidx.media.AudioAttributesCompat
import kotlin.properties.Delegates.observable
import kotlin.time.Duration

/**
 * The SoundPlayer interface represents a player for a sound entry from the Sound Library Manifest.
 * The interface provides methods to set fade-in and fade-out durations, enable/disable premium
 * segments, set the audio bitrate, and adjust the volume. It also provides methods to start, pause,
 * and stop playback.
 *
 * [SoundPlayer.State] enum represents playback states of the sound player. The player initialises
 * in [SoundPlayer.State.PAUSED] state. [SoundPlayer.State.STOPPED] is a terminal state in a sound
 * player's lifecycle. Once the sound player reaches this state, it cannot transition to another
 * state. [SoundPlayer.State.PAUSING] and [SoundPlayer.State.STOPPING] are special states that the
 * player temporarily assumes to indicate an on-going fade transition on issuing a pause or stop
 * command.
 */
abstract class SoundPlayer {

  private var stateChangeListener: StateChangeListener? = null

  /**
   * The current [SoundPlayer.State] of this [SoundPlayer]. Every change in its value invokes the
   * registered [StateChangeListener].
   */
  var state: State by observable(State.PAUSED) { _, oldValue, newValue ->
    if (oldValue != newValue) stateChangeListener?.onSoundPlayerStateChanged(newValue)
  }
    protected set

  /**
   * Sets the duration of the fade-in effect when starting playback.
   */
  abstract fun setFadeInDuration(duration: Duration)

  /**
   * Sets the duration of the fade-out effect when pausing or stopping playback.
   */
  abstract fun setFadeOutDuration(duration: Duration)

  /**
   * Enables or disables premium segments. Premium segments are parts of the sound that require a
   * premium subscription to be played.
   */
  abstract fun setPremiumSegmentsEnabled(enabled: Boolean)

  /**
   * Sets the audio bitrate of the sound player.
   *
   * @param bitrate acceptable values are `128k`, `192k`, `256k` and `320k`.
   */
  abstract fun setAudioBitrate(bitrate: String)

  /**
   * Sets the attributes for audio playback using an [AudioAttributesCompat] instance, used by the
   * underlying playback mechanism.
   */
  abstract fun setAudioAttributes(attrs: AudioAttributesCompat)

  /**
   * Sets the volume of the player.
   *
   * @param volume must be in range [0, 1]
   */
  abstract fun setVolume(volume: Float)

  /**
   * Starts playback of the sound.
   *
   * It transitions the player to either [State.BUFFERING] or [State.PLAYING] depending on
   * whether the playback is ready to begin immediately.
   */
  abstract fun play()

  /**
   * Pauses playback of the sound.
   *
   * It transitions the player to [State.PAUSING] and then eventually to [State.PAUSED] if
   * [immediate] is `false`. It immediately transitions the player to [State.PAUSED] otherwise.
   *
   * @param immediate whether the pause should be immediate or if the player should perform a
   * fade-out effect before pausing.
   */
  abstract fun pause(immediate: Boolean)

  /**
   * Stops playback of the sound.
   *
   * It transitions the player to [State.STOPPING] and then eventually to [State.STOPPED] if
   * [immediate] is `false`. It immediately transitions the player to [State.STOPPED] otherwise.
   *
   * @param immediate whether the stop should be immediate or if the player should perform a
   * fade-out effect before stopping.
   */
  abstract fun stop(immediate: Boolean)

  /**
   * Registers a [StateChangeListener] that gets invoked every time the playback state of this sound
   * player changes.
   */
  fun setStateChangeListener(listener: StateChangeListener?) {
    stateChangeListener = listener
  }

  /**
   * Represents the current playback state of a [SoundPlayer].
   */
  enum class State {
    /**
     * The player is loading data before it can begin its playback.
     */
    BUFFERING,

    /**
     * The player has enough data in its buffers to continue playback and is continuously refilling
     * its depleting buffer.
     */
    PLAYING,

    /**
     * The playback is in a fade-out transition after which the player will assume the [PAUSED]
     * state.
     */
    PAUSING,

    /**
     * The playback is paused.
     */
    PAUSED,

    /**
     * The playback is in a fade-out transition after which the player will assume the [STOPPED]
     * state.
     */
    STOPPING,

    /**
     * The playback is paused and the player has completed its lifecycle.
     */
    STOPPED,
  }

  /**
   * A listener interface for observing changes in the playback state of a [SoundPlayer] instance.
   */
  fun interface StateChangeListener {

    /**
     * Invoked when the playback state changes.
     * @param state the new [SoundPlayer.State] of the [SoundPlayer].
     */
    fun onSoundPlayerStateChanged(state: State)
  }

  /**
   * A factory for creating new SoundPlayer instances.
   */
  interface Factory {

    /**
     * Creates a new [SoundPlayer] instance for the specified [soundId].
     *
     * @param soundId ID of the sound that the [SoundPlayer] will play.
     * @return A [SoundPlayer] instance for playing the specified sound.
     */
    fun buildPlayer(soundId: String): SoundPlayer
  }
}

package com.github.ashutoshgngwr.noice.engine.media

import androidx.media3.common.AudioAttributes
import kotlin.properties.Delegates.observable
import kotlin.time.Duration

/**
 * [MediaPlayer] declares a simple interface for playing media files using a local audio sink.
 *
 * [MediaPlayer.State] enum represents playback states of the media player. The player initialises
 * in [MediaPlayer.State.PAUSED] state. [SoundPlayer.State.STOPPED] is a terminal state in a media
 * player's lifecycle. Once the media player reaches this state, it must not be used any further.
 */
abstract class MediaPlayer {

  protected var listener: Listener? = null; private set

  /**
   * Represents the current state of the media player. Every change in its value invokes
   * [Listener.onMediaPlayerStateChanged] all registered listeners.
   */
  var state: State by observable(State.PAUSED) { _, oldValue, newValue ->
    if (oldValue != newValue) listener?.onMediaPlayerStateChanged(newValue)
  }
    protected set

  /**
   * Registers a new [Listener] instance for listening to media player events. Setting a new
   * listener will replace any previously registered listener.
   */
  fun setListener(listener: Listener?) {
    this.listener = listener
  }

  /**
   * Sets the attributes for audio playback using an [AudioAttributes] instance, used by the
   * underlying local audio sink.
   */
  abstract fun setAudioAttributes(attrs: AudioAttributes)

  /**
   * Sets the audio volume.
   *
   * @param volume must be >= 0 and <= 1.
   * @throws IllegalArgumentException if the [volume] is out of the supported range.
   */
  abstract fun setVolume(volume: Float)

  /**
   * Attempts to start the audio playback.
   *
   * It transitions the media player to [State.IDLE] if the playlist is empty. Otherwise, it
   * transitions the player to [State.BUFFERING] or [State.PLAYING] depending on whether the
   * playback is ready to begin immediately.
   *
   * @throws IllegalStateException if the media player is in [State.STOPPED].
   */
  abstract fun play()

  /**
   * Pauses the audio playback and transitions the media player to [State.PAUSED].
   */
  abstract fun pause()

  /**
   * Stops the audio playback and transitions the media player to [State.STOPPED].
   */
  abstract fun stop()

  /**
   * Adds a new media item to the playlist.
   *
   * If the media player is in [State.IDLE], it attempts to start the playback and transitions the
   * player to [State.BUFFERING] or [State.PLAYING].
   */
  abstract fun addToPlaylist(uri: String)

  /**
   * Clears all media items from the playlist.
   */
  abstract fun clearPlaylist()

  /**
   * @return the number of remaining media items in the playlist including the one that is currently
   * playing.
   */
  abstract fun getRemainingItemCount(): Int

  /**
   * Fades the audio volume of the media player in or out over the specified [duration] and invokes
   * the given [callback] when finished.
   *
   * @param toVolume must be >= 0 and <= 1.
   * @throws IllegalArgumentException if [toVolume] is out of the supported range.
   */
  abstract fun fadeTo(toVolume: Float, duration: Duration, callback: () -> Unit = {})


  /**
   * Represents the current playback state of a [MediaPlayer].
   */
  enum class State {
    /**
     * The player is waiting for new items to play and will begin playback as soon as they're added.
     */
    IDLE,

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
     * The playback is paused.
     */
    PAUSED,

    /**
     * The playback is stopped.
     */
    STOPPED,
  }

  /**
   * A listener interface for observing changes in a [MediaPlayer] instance.
   */
  interface Listener {

    /**
     * Invoked when the playback state changes.
     * @param state the new [MediaPlayer.State] of the [MediaPlayer].
     */
    fun onMediaPlayerStateChanged(state: State)

    /**
     * Invoked every time the media player ends playing one media item and moves onto the next one
     * in the playlist.
     *
     * It also gets invoked on adding the first media item to the playlist or when it finishes
     * playing the last media item in the playlist.
     */
    fun onMediaPlayerItemTransition()
  }

  /**
   * A factory for creating new [MediaPlayer] instances.
   */
  interface Factory {
    fun buildPlayer(): MediaPlayer
  }
}

package com.github.ashutoshgngwr.noice.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.os.HandlerCompat
import androidx.core.os.postDelayed
import androidx.media.AudioAttributesCompat
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.RenderersFactory
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import kotlin.math.abs
import kotlin.properties.Delegates.observable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [MediaPlayer] wraps [ExoPlayer] APIs and streamlines them for use in a sound player. It provides
 * methods to control media playback, manipulate playlist and audio attributes, and fade audio
 * volume.
 *
 * [MediaPlayer.State] enum represents playback states of the media player. The player initialises
 * in [MediaPlayer.State.PAUSED] state. [SoundPlayer.State.STOPPED] is a terminal state in a media
 * player's lifecycle. Once the media player reaches this state, it must not be used any further.
 */
class MediaPlayer @VisibleForTesting constructor(
  private val exoPlayer: ExoPlayer,
) : Player.Listener {

  private var retryDelay = MIN_RETRY_DELAY
  private var hasExoPlayerErred = false

  private val handler = Handler(Looper.getMainLooper())
  private val listeners = mutableSetOf<Listener>()

  /**
   * Represents the current state of the media player. Every change in its value invokes
   * [Listener.onMediaPlayerStateChanged] all registered listeners.
   */
  var state: State by observable(State.PAUSED) { _, oldValue, newValue ->
    if (oldValue != newValue) listeners.forEach { it.onMediaPlayerStateChanged(newValue) }
  }
    private set

  init {
    exoPlayer.addListener(this)
  }

  override fun onEvents(player: Player, events: Player.Events) {
    if (!exoPlayer.playWhenReady) {
      return
    }

    if (exoPlayer.isPlaying) {
      retryDelay = MIN_RETRY_DELAY
      hasExoPlayerErred = false
      state = State.PLAYING
    } else if (exoPlayer.isLoading || hasExoPlayerErred) {
      state = State.BUFFERING
    } else {
      state = State.IDLE
    }
  }

  override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    if (exoPlayer.currentMediaItemIndex > 0) {
      exoPlayer.removeMediaItems(0, exoPlayer.currentMediaItemIndex)
    }

    listeners.forEach { it.onMediaPlayerItemTransition() }
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    // When ExoPlayer finishes playing the last media item, it doesn't emit a media item transition
    // event. So, handle clearing the playlist here.
    if (playbackState == Player.STATE_ENDED) {
      val playWhenReady = exoPlayer.playWhenReady
      exoPlayer.stop() // transition to idle state so that adding items to playlist works correctly.
      exoPlayer.clearMediaItems()
      exoPlayer.playWhenReady = playWhenReady
    }
  }

  override fun onPlayerError(error: PlaybackException) {
    hasExoPlayerErred = true
    handler.removeCallbacksAndMessages(RETRY_CALLBACK_TOKEN)
    Log.d(LOG_TAG, "onPlayerError: retrying playback in $retryDelay")
    handler.postDelayed(retryDelay.inWholeMilliseconds, RETRY_CALLBACK_TOKEN) {
      exoPlayer.prepare()
    }

    retryDelay = minOf(retryDelay * 2, MAX_RETRY_DELAY)
  }

  /**
   * Sets the attributes for audio playback using an [AudioAttributesCompat] instance, used by the
   * underlying [ExoPlayer] instance.
   */
  @SuppressLint("WrongConstant")
  fun setAudioAttributes(attrs: AudioAttributesCompat) {
    AudioAttributes.Builder()
      .setContentType(attrs.contentType)
      .setFlags(attrs.flags)
      .setUsage(attrs.usage)
      .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_NONE)
      .build()
      .also { exoPlayer.setAudioAttributes(it, false) }
  }

  /**
   * Sets the audio volume.
   *
   * @param volume must be >= 0 and <= 1.
   * @throws IllegalArgumentException if the [volume] is out of the supported range.
   */
  fun setVolume(volume: Float) {
    require(volume in 0F..1F) { "audio volume must be range [0, 1]" }
    exoPlayer.volume = volume
  }

  /**
   * Attempts to start the audio playback.
   *
   * It transitions the media player to [State.IDLE] if the playlist is empty. Otherwise, it
   * transitions the player to [State.BUFFERING] or [State.PLAYING] depending on whether the
   * playback is ready to begin immediately.
   *
   * @throws IllegalStateException if the media player is in [State.STOPPED].
   */
  fun play() {
    if (state == State.STOPPED) {
      throw IllegalStateException("must not re-use a stopped player")
    }

    exoPlayer.prepare()
    exoPlayer.playWhenReady = true
  }

  /**
   * Pauses the audio playback and transitions the media player to [State.PAUSED].
   */
  fun pause() {
    exoPlayer.pause()
    state = State.PAUSED
  }

  /**
   * Stops the audio playback and transitions the media player to [State.STOPPED].
   */
  fun stop() {
    handler.removeCallbacksAndMessages(FADE_CALLBACK_TOKEN)
    handler.removeCallbacksAndMessages(RETRY_CALLBACK_TOKEN)
    exoPlayer.removeListener(this)
    exoPlayer.stop()
    exoPlayer.release()
    state = State.STOPPED
  }

  /**
   * Adds a new media item to the playlist.
   *
   * If the media player is in [State.IDLE], it attempts to start the playback and transitions the
   * player to [State.BUFFERING] or [State.PLAYING].
   */
  fun addToPlaylist(uri: String) {
    exoPlayer.addMediaItem(MediaItem.fromUri(uri))
    exoPlayer.prepare()
  }

  /**
   * Clears all media items from the playlist.
   */
  fun clearPlaylist() {
    exoPlayer.clearMediaItems()
  }

  /**
   * @return the number of remaining media items in the playlist including the one that is currently
   * playing.
   */
  fun getRemainingItemCount(): Int {
    return exoPlayer.mediaItemCount
  }

  /**
   * Fades the audio volume of the media player in or out over the specified [duration] and invokes
   * the given [callback] when finished.
   *
   * @param toVolume must be >= 0 and <= 1.
   * @throws IllegalArgumentException if [toVolume] is out of the supported range.
   */
  fun fadeTo(toVolume: Float, duration: Duration, callback: () -> Unit = {}) {
    require(toVolume in 0F..1F) { "toVolume must be in range [0, 1]" }
    handler.removeCallbacksAndMessages(FADE_CALLBACK_TOKEN)

    if (duration == Duration.ZERO || !exoPlayer.isPlaying) {
      setVolume(toVolume)
      callback.invoke()
      return
    }

    val startMillis = System.currentTimeMillis()
    val durationMillis = duration.inWholeMilliseconds
    val fromVolume = exoPlayer.volume
    val deltaVolume = abs(fromVolume - toVolume)
    val sign = if (fromVolume > toVolume) -1 else 1

    val fadeCallback = object : Runnable {
      override fun run() {
        val progress = (System.currentTimeMillis() - startMillis).toFloat() / durationMillis
        val newVolume = fromVolume + (deltaVolume * progress * sign)
        if ((sign < 0 && newVolume <= toVolume) || (sign > 0 && newVolume >= toVolume)) {
          exoPlayer.volume = toVolume
          callback.invoke()
        } else {
          exoPlayer.volume = newVolume
          HandlerCompat.postDelayed(handler, this, FADE_CALLBACK_TOKEN, 50)
        }
      }
    }

    fadeCallback.run()
  }

  /**
   * Registers a new [Listener] instance for listening to media player events.
   */
  fun addListener(listener: Listener) {
    listeners.add(listener)
  }

  /**
   * Unregisters a previously registered [Listener] instance.
   */
  fun removeListener(listener: Listener) {
    listeners.remove(listener)
  }

  companion object {
    private const val LOG_TAG = "MediaPlayer"
    private const val FADE_CALLBACK_TOKEN = "MediaPlayer.fadeCallback"
    private const val RETRY_CALLBACK_TOKEN = "MediaPlayer.retryCallback"

    private val MIN_RETRY_DELAY = 1.seconds
    private val MAX_RETRY_DELAY = 30.seconds
  }

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

  class Factory(private val context: Context, exoPlayerDataSourceFactory: DataSource.Factory) {

    private val mediaSourceFactory = ExtractorsFactory { arrayOf(Mp3Extractor()) }
      .let { ProgressiveMediaSource.Factory(exoPlayerDataSourceFactory, it) }

    private val renderersFactory = RenderersFactory { handler, _, audioListener, _, _ ->
      arrayOf(MediaCodecAudioRenderer(context, MediaCodecSelector.DEFAULT, handler, audioListener))
    }

    fun buildPlayer(): MediaPlayer {
      return MediaPlayer(
        ExoPlayer.Builder(context, renderersFactory, mediaSourceFactory)
          .setLoadControl(
            DefaultLoadControl.Builder()
              .setBufferDurationsMs(
                10_000,
                20_000,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
              )
              .build()
          )
          .build()
      )
    }
  }
}

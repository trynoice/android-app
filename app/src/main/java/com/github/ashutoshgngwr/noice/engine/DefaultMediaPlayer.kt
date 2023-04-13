package com.github.ashutoshgngwr.noice.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.os.HandlerCompat
import androidx.core.os.postDelayed
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [DefaultMediaPlayer] wraps [ExoPlayer] APIs and streamlines them for use in a sound player. It
 * provides methods to control media playback, manipulate playlist and audio attributes, and fade
 * audio volume.
 */
class DefaultMediaPlayer @VisibleForTesting constructor(
  private val exoPlayer: ExoPlayer,
) : MediaPlayer(), Player.Listener {

  private var retryDelay = MIN_RETRY_DELAY
  private var hasExoPlayerErred = false

  private val handler = Handler(Looper.getMainLooper())

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

    listener?.onMediaPlayerItemTransition()
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

  override fun setAudioAttributes(attrs: AudioAttributes) {
    exoPlayer.setAudioAttributes(attrs, false)
  }

  override fun setVolume(volume: Float) {
    require(volume in 0F..1F) { "audio volume must be range [0, 1]" }
    exoPlayer.volume = volume
  }

  override fun play() {
    if (state == State.STOPPED) {
      throw IllegalStateException("must not re-use a stopped player")
    }

    exoPlayer.prepare()
    exoPlayer.playWhenReady = true
  }

  override fun pause() {
    exoPlayer.pause()
    state = State.PAUSED
  }

  override fun stop() {
    handler.removeCallbacksAndMessages(FADE_CALLBACK_TOKEN)
    handler.removeCallbacksAndMessages(RETRY_CALLBACK_TOKEN)
    exoPlayer.removeListener(this)
    exoPlayer.stop()
    exoPlayer.release()
    state = State.STOPPED
  }

  override fun addToPlaylist(uri: String) {
    exoPlayer.addMediaItem(MediaItem.fromUri(uri))
    exoPlayer.prepare()
  }

  override fun clearPlaylist() {
    exoPlayer.clearMediaItems()
  }

  override fun getRemainingItemCount(): Int {
    return exoPlayer.mediaItemCount
  }

  override fun fadeTo(toVolume: Float, duration: Duration, callback: () -> Unit) {
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

  companion object {
    private const val LOG_TAG = "DefaultMediaPlayer"
    private const val FADE_CALLBACK_TOKEN = "DefaultMediaPlayer.fadeCallback"
    private const val RETRY_CALLBACK_TOKEN = "DefaultMediaPlayer.retryCallback"

    private val MIN_RETRY_DELAY = 1.seconds
    private val MAX_RETRY_DELAY = 30.seconds
  }

  class Factory(
    private val context: Context,
    mediaDataSourceFactory: DataSource.Factory,
  ) : MediaPlayer.Factory {

    private val mediaSourceFactory = ExtractorsFactory { arrayOf(Mp3Extractor()) }
      .let { ProgressiveMediaSource.Factory(mediaDataSourceFactory, it) }

    private val renderersFactory = RenderersFactory { handler, _, audioListener, _, _ ->
      arrayOf(MediaCodecAudioRenderer(context, MediaCodecSelector.DEFAULT, handler, audioListener))
    }

    override fun buildPlayer(): MediaPlayer {
      return DefaultMediaPlayer(
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

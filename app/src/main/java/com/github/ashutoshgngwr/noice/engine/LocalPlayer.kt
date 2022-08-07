package com.github.ashutoshgngwr.noice.engine

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.MediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import com.google.android.exoplayer2.Player as IExoPlayer

/**
 * A [Player] implementation that plays the sound using the audio output on the host device.
 */
class LocalPlayer(
  context: Context,
  soundId: String,
  audioBitrate: String,
  audioAttributes: AudioAttributesCompat,
  soundRepository: SoundRepository,
  mediaSourceFactory: MediaSource.Factory,
  externalScope: CoroutineScope,
  playbackListener: PlaybackListener,
) : Player(soundId, audioBitrate, soundRepository, externalScope, playbackListener),
  IExoPlayer.Listener {

  private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
    .setMediaSourceFactory(mediaSourceFactory)
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

  override var audioAttributes: AudioAttributesCompat = audioAttributes
    set(value) {
      field = value
      exoPlayer.setAudioAttributesCompat(value, false)
    }

  private var fadeAnimator: ValueAnimator? = null
  private var skipNextFadeInTransition: Boolean = false
  private var retryDelayMillis = MIN_RETRY_DELAY_MILLIS

  init {
    exoPlayer.volume = 0F // muted initially (for the fade-in effect)
    exoPlayer.addListener(this)
    exoPlayer.setAudioAttributesCompat(audioAttributes, false)
  }

  override fun onIsPlayingChanged(isPlaying: Boolean) {
    if (isPlaying) {
      retryDelayMillis = MIN_RETRY_DELAY_MILLIS
      setPlaybackState(PlaybackState.PLAYING)
      // fade-in or restore volume whenever player starts after buffering or paused states.
      if (sound?.isContiguous == true && !skipNextFadeInTransition) {
        skipNextFadeInTransition = false
        exoPlayer.fade(0F, getScaledVolume(), fadeInDuration.inWholeMilliseconds)
      } else {
        exoPlayer.volume = getScaledVolume()
      }
    }
  }

  override fun onIsLoadingChanged(isLoading: Boolean) {
    // This event is delivered whenever ExoPlayer is loading data, even if it is currently playing.
    // Therefore, we set only buffering state when our player isn't actually playing, but is about
    // to play.
    if (!isLoading) {
      return
    }

    if (exoPlayer.playWhenReady && !exoPlayer.isPlaying) {
      setPlaybackState(PlaybackState.BUFFERING)
    }
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    // only request next segment for non-contiguous sounds when the current segment has finished
    // playing. Otherwise, the silence period will start before the current segment is over.
    if (playbackState == IExoPlayer.STATE_ENDED && sound?.isContiguous == false) {
      Log.d(LOG_TAG, "onPlaybackStateChanged: requesting next segment")
      requestNextSegment()
    }
  }

  override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    // request next segment for contiguous sounds whenever ExoPlayer is playing the last item in its
    // playlist to ensure gap-less playback.
    if (sound?.isContiguous == true && !exoPlayer.hasNextMediaItem()) {
      Log.d(LOG_TAG, "onMediaItemTransition: requesting next segment")
      requestNextSegment()
    }
  }

  override fun onPlayerError(error: PlaybackException) {
    defaultScope.launch {
      retryDelayMillis.toDuration(DurationUnit.MILLISECONDS)
        .also { Log.d(LOG_TAG, "onPlayerError: retrying playback in $it") }

      delay(retryDelayMillis)
      retryDelayMillis = min(retryDelayMillis * 2, MAX_RETRY_DELAY_MILLIS)
      exoPlayer.prepare()
    }
  }

  override fun resetSegmentQueue() {
    val wasPlaying = exoPlayer.playWhenReady
    skipNextFadeInTransition = exoPlayer.isPlaying && fadeAnimator?.isRunning != true
    if (wasPlaying) {
      exoPlayer.stop()
    }

    exoPlayer.clearMediaItems()
    exoPlayer.playWhenReady = wasPlaying

    // `ExoPlayer.clearMediaItems` triggers `onMediaItemTransition` callback so it will
    // automatically `requestNextSegment` for contiguous sounds.
    if (sound?.isContiguous == false) {
      requestNextSegment()
    }
  }

  override fun playInternal() {
    if (exoPlayer.isPlaying) { // maybe pausing or stopping state.
      if (exoPlayer.volume != getScaledVolume()) {
        exoPlayer.fade(exoPlayer.volume, getScaledVolume(), fadeInDuration.inWholeMilliseconds)
      }

      setPlaybackState(PlaybackState.PLAYING)
      return
    }

    setPlaybackState(PlaybackState.BUFFERING)
    exoPlayer.playWhenReady = true
    if (exoPlayer.mediaItemCount == 0) {
      requestNextSegment()
    } else {
      exoPlayer.prepare()
    }
  }

  override fun pause(immediate: Boolean) {
    if (immediate || !exoPlayer.isPlaying) {
      pauseImmediately()
      return
    }

    setPlaybackState(PlaybackState.PAUSING)
    exoPlayer.fade(exoPlayer.volume, 0F, fadeOutDuration.inWholeMilliseconds) {
      pauseImmediately()
    }
  }

  private fun pauseImmediately() {
    setPlaybackState(PlaybackState.PAUSED)
    exoPlayer.pause()
  }

  override fun stop(immediate: Boolean) {
    if (immediate || !exoPlayer.isPlaying) {
      stopImmediately()
      return
    }

    setPlaybackState(PlaybackState.STOPPING)
    exoPlayer.fade(exoPlayer.volume, 0F, fadeOutDuration.inWholeMilliseconds) {
      stopImmediately()
    }
  }

  private fun stopImmediately() {
    setPlaybackState(PlaybackState.STOPPED)
    exoPlayer.stop()
    exoPlayer.release()
  }

  override fun setVolumeInternal(volume: Float) {
    if (exoPlayer.isPlaying) {
      exoPlayer.fade(exoPlayer.volume, volume, 1_500L)
    } else {
      exoPlayer.volume = getScaledVolume()
    }
  }

  override fun onSegmentAvailable(uri: String) {
    exoPlayer.addMediaItem(MediaItem.fromUri(uri))
    exoPlayer.prepare()
  }

  @SuppressLint("WrongConstant")
  private fun ExoPlayer.setAudioAttributesCompat(
    attrsCompat: AudioAttributesCompat,
    handleAudioFocus: Boolean,
  ) {
    AudioAttributes.Builder()
      .setContentType(attrsCompat.contentType)
      .setFlags(attrsCompat.flags)
      .setUsage(attrsCompat.usage)
      .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_NONE)
      .build()
      .also { setAudioAttributes(it, handleAudioFocus) }
  }

  private inline fun ExoPlayer.fade(
    fromVolume: Float,
    toVolume: Float,
    durationMillis: Long,
    crossinline callback: () -> Unit = {},
  ) {
    // clearing previous callback is required because a Player might want to transition from
    // `STOPPING` to `PLAYING` state. If the callback isn't cleared, the callback for fade-out
    // during stop operation will make ExoPlayer release its resources.
    fadeAnimator?.removeAllListeners()
    fadeAnimator?.cancel()
    fadeAnimator = ValueAnimator.ofFloat(fromVolume, toVolume).apply {
      duration = durationMillis
      addUpdateListener { volume = it.animatedValue as Float }
      doOnStart { volume = fromVolume }
      doOnEnd { callback.invoke() }
      start()
    }
  }

  companion object {
    private const val LOG_TAG = "LocalPlayer"
    private const val MIN_RETRY_DELAY_MILLIS = 1 * 1000L
    private const val MAX_RETRY_DELAY_MILLIS = 30 * 1000L
  }

  /**
   * A factory to [LocalPlayer] instances.
   */
  class Factory(
    private val context: Context,
    private val mediaSourceFactory: MediaSource.Factory,
  ) : Player.Factory {

    override fun createPlayer(
      soundId: String,
      soundRepository: SoundRepository,
      audioBitrate: String,
      audioAttributes: AudioAttributesCompat,
      defaultScope: CoroutineScope,
      playbackListener: PlaybackListener
    ): Player {
      return LocalPlayer(
        context,
        soundId,
        audioBitrate,
        audioAttributes,
        soundRepository,
        mediaSourceFactory,
        defaultScope,
        playbackListener,
      )
    }
  }
}

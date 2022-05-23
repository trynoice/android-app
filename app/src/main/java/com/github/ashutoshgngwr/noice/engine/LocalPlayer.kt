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
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.util.EventLogger
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
  soundRepository: SoundRepository,
  mediaSourceFactory: MediaSource.Factory,
  audioAttributes: AudioAttributesCompat,
  externalScope: CoroutineScope,
  playbackListener: PlaybackListener,
) : Player(soundId, soundRepository, externalScope, playbackListener), IExoPlayer.Listener {

  private val trackSelector = DefaultTrackSelector(
    // mixed channel count and mixed sample rate must be enabled because our HLS master playlists
    // lack information about the sample rate and channel count for variant streams.
    DefaultTrackSelector.ParametersBuilder(context)
      .setAllowAudioMixedChannelCountAdaptiveness(true)
      .setAllowAudioMixedSampleRateAdaptiveness(true)
      .build(),
    AdaptiveTrackSelection.Factory()
  )

  private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
    .setTrackSelector(trackSelector)
    .setMediaSourceFactory(mediaSourceFactory)
    .build()

  override var audioAttributes: AudioAttributesCompat = audioAttributes
    set(value) {
      field = value
      exoPlayer.setAudioAttributesCompat(value, false)
    }

  private var fadeAnimator: ValueAnimator? = null
  private var retryDelayMillis = MIN_RETRY_DELAY_MILLIS

  init {
    exoPlayer.volume = 0F // muted initially (for the fade-in effect)
    exoPlayer.addListener(this)
    exoPlayer.addAnalyticsListener(EventLogger(trackSelector))
    exoPlayer.setAudioAttributesCompat(audioAttributes, false)
  }

  override fun onIsPlayingChanged(isPlaying: Boolean) {
    if (isPlaying) {
      retryDelayMillis = MIN_RETRY_DELAY_MILLIS
      setPlaybackState(PlaybackState.PLAYING)
      // fade-in or restore volume whenever player starts after buffering or paused states.
      if (sound?.isContiguous == true) {
        exoPlayer.fade(0F, getScaledVolume(), fadeInDuration.inWholeMilliseconds)
      } else {
        exoPlayer.volume = getScaledVolume()
      }
    }
  }

  override fun onIsLoadingChanged(isLoading: Boolean) {
    // This event is delivered whenever ExoPlayer is loading data, even if it is currently playing.
    // Therefore, we set only buffering state when our player isn't actually playing.
    if (isLoading && !exoPlayer.isPlaying) {
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

  override fun setMaxAudioBitrate(bitrate: Int) {
    trackSelector.parameters = trackSelector.parameters.buildUpon()
      .setMaxAudioBitrate(bitrate)
      .build()
  }

  override fun playInternal() {
    if (exoPlayer.isPlaying) { // maybe pausing or stopping state.
      if (exoPlayer.volume != getScaledVolume()) {
        exoPlayer.fade(exoPlayer.volume, getScaledVolume(), fadeInDuration.inWholeMilliseconds)
      }

      setPlaybackState(PlaybackState.PLAYING)
      return
    }

    exoPlayer.playWhenReady = true
    if (exoPlayer.mediaItemCount == 0) {
      requestNextSegment()
    }
  }

  override fun pause(immediate: Boolean) {
    setPlaybackState(PlaybackState.PAUSING)
    val fadeOutDurationMs = if (immediate || !exoPlayer.isPlaying) {
      0
    } else {
      fadeOutDuration.inWholeMilliseconds
    }

    exoPlayer.fade(exoPlayer.volume, 0F, fadeOutDurationMs) {
      setPlaybackState(PlaybackState.PAUSED)
      exoPlayer.pause()
    }
  }

  override fun stop(immediate: Boolean) {
    setPlaybackState(PlaybackState.STOPPING)
    val fadeOutDurationMs = if (immediate || !exoPlayer.isPlaying) {
      0
    } else {
      fadeOutDuration.inWholeMilliseconds
    }

    exoPlayer.fade(exoPlayer.volume, 0F, fadeOutDurationMs) {
      setPlaybackState(PlaybackState.STOPPED)
      exoPlayer.stop()
      exoPlayer.release()
    }
  }

  override fun setVolumeInternal(volume: Float) {
    exoPlayer.fade(exoPlayer.volume, volume, 1_500L)
  }

  override fun onSegmentAvailable(segment: Segment) {
    exoPlayer.addMediaItem(MediaItem.fromUri("noice://cdn/${segment.path}"))
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
      audioAttributes: AudioAttributesCompat,
      defaultScope: CoroutineScope,
      playbackListener: PlaybackListener
    ): Player {
      return LocalPlayer(
        context,
        soundId,
        soundRepository,
        mediaSourceFactory,
        audioAttributes,
        defaultScope,
        playbackListener,
      )
    }
  }
}

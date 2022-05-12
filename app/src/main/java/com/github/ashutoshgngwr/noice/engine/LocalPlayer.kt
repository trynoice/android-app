package com.github.ashutoshgngwr.noice.engine

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
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
import com.google.android.exoplayer2.Player as IExoPlayer

/**
 * A [Player] implementation that plays the sound using the audio output on the host device.
 */
class LocalPlayer(
  context: Context,
  override val soundId: String,
  override val soundRepository: SoundRepository,
  mediaSourceFactory: MediaSource.Factory,
  audioAttributes: AudioAttributesCompat,
  override val defaultScope: CoroutineScope,
  override val playbackListener: PlaybackListener,
) : Player(), IExoPlayer.Listener {

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

  init {
    exoPlayer.volume = 0F // muted initially (for the fade-in effect)
    exoPlayer.addListener(this)
    exoPlayer.addAnalyticsListener(EventLogger(trackSelector))
    exoPlayer.setAudioAttributesCompat(audioAttributes, false)
  }

  override fun onIsPlayingChanged(isPlaying: Boolean) {
    if (isPlaying) {
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
      requestNextSegment()
    }
  }

  override fun onPlayerError(error: PlaybackException) {
    exoPlayer.stop()
    setPlaybackState(PlaybackState.FAILED)
  }

  override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    // request next segment for contiguous sounds whenever ExoPlayer is playing the last item in its
    // playlist.
    if (sound?.isContiguous == true && !exoPlayer.hasNextMediaItem()) {
      requestNextSegment()
    }
  }

  override fun setMaxAudioBitrate(bitrate: Int) {
    trackSelector.parameters = trackSelector.parameters.buildUpon()
      .setMaxAudioBitrate(bitrate)
      .build()
  }

  override fun playInternal() {
    if (exoPlayer.isPlaying) {
      // stop fade-out animation if any, then resume playing and regain lost volume.
      if (fadeAnimator?.isStarted == true || fadeAnimator?.isRunning == true) {
        fadeAnimator?.removeAllListeners()
        fadeAnimator?.cancel()
      }

      if (exoPlayer.volume != getScaledVolume()) {
        exoPlayer.fade(exoPlayer.volume, getScaledVolume(), fadeInDuration.inWholeMilliseconds)
      }

      return
    }

    exoPlayer.playWhenReady = true
    if (exoPlayer.mediaItemCount == 0) {
      requestNextSegment()
    }
  }

  override fun pause(immediate: Boolean) {
    val fadeOutDurationMs = if (immediate) {
      0
    } else {
      fadeOutDuration.inWholeMilliseconds
    }

    setPlaybackState(PlaybackState.PAUSING)
    exoPlayer.fade(exoPlayer.volume, 0F, fadeOutDurationMs) {
      setPlaybackState(PlaybackState.PAUSED)
      exoPlayer.pause()
    }
  }

  override fun stop() {
    setPlaybackState(PlaybackState.STOPPING)
    exoPlayer.fade(exoPlayer.volume, 0F, fadeOutDuration.inWholeMilliseconds) {
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
    fadeAnimator?.cancel()
    fadeAnimator = ValueAnimator.ofFloat(fromVolume, toVolume).apply {
      duration = durationMillis
      addUpdateListener { volume = it.animatedValue as Float }
      doOnStart { volume = fromVolume }
      doOnEnd { callback.invoke() }
      start()
    }
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

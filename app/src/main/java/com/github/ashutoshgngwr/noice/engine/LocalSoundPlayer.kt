package com.github.ashutoshgngwr.noice.engine

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.media3.common.AudioAttributes
import com.github.ashutoshgngwr.noice.models.Sound
import com.github.ashutoshgngwr.noice.models.SoundSegment
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A [SoundPlayer] implementation the plays sounds using the local audio sink available on the
 * device.
 */
class LocalSoundPlayer @VisibleForTesting constructor(
  soundMetadataSource: SoundMetadataSource,
  private val mediaPlayer: MediaPlayer,
  private val defaultScope: CoroutineScope,
) : SoundPlayer(), MediaPlayer.Listener {

  private var fadeInDuration = Duration.ZERO
  private var fadeOutDuration = Duration.ZERO
  private var isPremiumSegmentsEnabled = false
  private var audioBitrate = "128k"
  private var volume = 1F

  private var hasLoadedMetadata = false
  private var retryDelay = MIN_RETRY_DELAY
  private var shouldPlayOnLoadingMetadata = false
  private var shouldFadeIn = false
  private var queueNextSegmentJob: Job? = null
  private var currentSegment: SoundSegment? = null
  private var maxSilenceSeconds = 0
  private val segments = mutableListOf<SoundSegment>()
  private val loadSoundMetadataJob = defaultScope.launch { loadSoundMetadata(soundMetadataSource) }

  init {
    mediaPlayer.setListener(this)
  }

  override fun onMediaPlayerStateChanged(state: MediaPlayer.State) {
    Log.d(LOG_TAG, "onMediaPlayerStateChanged: state=${state}")
    if (shouldFadeIn && state == MediaPlayer.State.PLAYING) {
      mediaPlayer.fadeTo(getScaledVolume(), fadeInDuration)
      shouldFadeIn = false
    }

    when (state) {
      MediaPlayer.State.BUFFERING -> this.state = State.BUFFERING
      MediaPlayer.State.PAUSED -> this.state = State.PAUSED
      MediaPlayer.State.STOPPED -> {
        mediaPlayer.setListener(null)
        this.state = State.STOPPED
      }
      else -> {
        // do not overwrite pausing and stopping state.
        if (this.state != State.PAUSING && this.state != State.STOPPING) {
          this.state = State.PLAYING
        }
      }
    }
  }

  override fun onMediaPlayerItemTransition() {
    if (state == State.STOPPED || !hasLoadedMetadata) {
      return
    }

    // non-contiguous sounds should only queue next segment when all previous media items have ended.
    if (maxSilenceSeconds > 0 && mediaPlayer.getRemainingItemCount() == 0) {
      val after = (30 + RANDOM.nextInt(maxSilenceSeconds - 30)).seconds
      Log.d(LOG_TAG, "queueNextSegment: scheduling next segment after $after")
      queueNextSegmentJob?.cancel()
      queueNextSegmentJob = defaultScope.launch {
        delay(after)
        queueNextSegment()
      }
    }

    // contiguous sound should always have 2 media items in the playlist.
    if (maxSilenceSeconds == 0 && mediaPlayer.getRemainingItemCount() < 2) {
      queueNextSegment()
    }
  }

  override fun setFadeInDuration(duration: Duration) {
    fadeInDuration = duration
  }

  override fun setFadeOutDuration(duration: Duration) {
    fadeOutDuration = duration
  }

  override fun setPremiumSegmentsEnabled(enabled: Boolean) {
    if (enabled == isPremiumSegmentsEnabled) {
      return
    }

    isPremiumSegmentsEnabled = enabled
    currentSegment = null
    mediaPlayer.clearPlaylist()
  }

  override fun setAudioBitrate(bitrate: String) {
    if (bitrate == audioBitrate) {
      return
    }

    audioBitrate = bitrate
    currentSegment = null
    mediaPlayer.clearPlaylist()
  }

  override fun setAudioAttributes(attrs: AudioAttributes) {
    mediaPlayer.setAudioAttributes(attrs)
  }

  override fun setVolume(volume: Float) {
    this.volume = volume
    if (mediaPlayer.state == MediaPlayer.State.PLAYING) {
      mediaPlayer.fadeTo(getScaledVolume(), 1.5.seconds)
    } else {
      mediaPlayer.setVolume(getScaledVolume())
    }
  }

  override fun play() {
    if (state == State.STOPPED) {
      throw IllegalStateException("cannot re-use a stopped sound player")
    }

    if (mediaPlayer.state == MediaPlayer.State.PLAYING) {
      state = State.PLAYING
      mediaPlayer.fadeTo(getScaledVolume(), fadeInDuration)
      return
    }

    if (!hasLoadedMetadata) {
      // set our state to buffering and auto start when the metadata finishes loading.
      shouldPlayOnLoadingMetadata = true
      state = State.BUFFERING
    } else {
      if (mediaPlayer.getRemainingItemCount() == 0) {
        queueNextSegment()
      }

      shouldFadeIn = true
      mediaPlayer.setVolume(0F)
      mediaPlayer.play()
    }
  }

  override fun pause(immediate: Boolean) {
    if (shouldPlayOnLoadingMetadata) {
      shouldPlayOnLoadingMetadata = false
      state = State.PAUSED
      return
    }

    queueNextSegmentJob?.cancel()
    if (immediate || mediaPlayer.state != MediaPlayer.State.PLAYING) {
      mediaPlayer.pause()
      return
    }

    state = State.PAUSING
    mediaPlayer.fadeTo(0F, fadeOutDuration) { mediaPlayer.pause() }
  }

  override fun stop(immediate: Boolean) {
    loadSoundMetadataJob.cancel()
    queueNextSegmentJob?.cancel()
    if (immediate || mediaPlayer.state != MediaPlayer.State.PLAYING) {
      mediaPlayer.stop()
      return
    }

    state = State.STOPPING
    mediaPlayer.fadeTo(0F, fadeOutDuration) { mediaPlayer.stop() }
  }

  private fun getScaledVolume(): Float {
    return volume.pow(2)
  }

  private suspend fun loadSoundMetadata(soundMetadataSource: SoundMetadataSource) {
    Log.d(LOG_TAG, "loadSoundMetadata: loading sound metadata")
    try {
      val sound = soundMetadataSource.load()
      maxSilenceSeconds = sound.info.maxSilence
      segments.clear()
      segments.addAll(sound.segments)
      hasLoadedMetadata = true
      retryDelay = MIN_RETRY_DELAY
      if (shouldPlayOnLoadingMetadata) {
        Log.d(LOG_TAG, "loadSoundMetadata: starting playback")
        shouldPlayOnLoadingMetadata = false
        play()
      }
    } catch (e: SoundMetadataSource.LoadException) {
      Log.w(LOG_TAG, "loadSoundMetadata: failed to load sound metadata", e)
      Log.i(LOG_TAG, "loadSoundMetadata: retrying in $retryDelay")
      delay(retryDelay)
      retryDelay = minOf(retryDelay * 2, MAX_RETRY_DELAY)
      loadSoundMetadata(soundMetadataSource)
    }
  }

  private fun queueNextSegment() {
    val validSegments = if (isPremiumSegmentsEnabled) segments else segments.filter { it.isFree }

    val nextSegment = when {
      currentSegment?.isBridgeSegment == true -> {
        validSegments.find { it.name == currentSegment?.to }
      }
      maxSilenceSeconds == 0 && currentSegment != null -> {
        val from = requireNotNull(currentSegment)
        validSegments.filter { it.isBridgeSegment && it.from == from.name }.random(RANDOM)
      }
      else -> validSegments.random(RANDOM)
    } ?: throw IllegalStateException("couldn't find a segment to queue next")

    Log.d(LOG_TAG, "queueNextSegment: queuing ${nextSegment.name}")
    currentSegment = nextSegment
    mediaPlayer.addToPlaylist("noice://cdn/library/${nextSegment.path(audioBitrate)}")
  }

  private fun <T> List<T>.random(random: Random): T {
    if (isEmpty()) {
      throw NoSuchElementException()
    }

    return get(random.nextInt(size))
  }

  companion object {
    private const val LOG_TAG = "LocalSoundPlayer"

    private val MIN_RETRY_DELAY = 1.seconds
    private val MAX_RETRY_DELAY = 30.seconds

    // Kotlin's random implementation generates the same number in the beginning at each restart.
    private val RANDOM = Random()
  }

  /**
   * An interface for abstracting sound metadata loader implementation.
   */
  fun interface SoundMetadataSource {

    /**
     * Loads sound metadata.
     *
     * @throws LoadException on failing to load the metadata.
     */
    suspend fun load(): Sound

    class LoadException(message: String, cause: Throwable?) : Exception(message, cause)
  }

  /**
   * An implementation of [SoundPlayer.Factory] for building [LocalSoundPlayer] instances.
   */
  class Factory(
    private val soundRepository: SoundRepository,
    private val mediaPlayerFactory: MediaPlayer.Factory,
    private val defaultScope: CoroutineScope,
  ) : SoundPlayer.Factory {

    override fun buildPlayer(soundId: String): SoundPlayer {
      return LocalSoundPlayer(
        soundMetadataSource = {
          val resource = soundRepository.get(soundId).lastOrNull()
          if (resource?.data == null) {
            throw SoundMetadataSource.LoadException(
              "couldn't load sound with id $soundId",
              resource?.error,
            )
          }

          resource.data
        },
        mediaPlayer = mediaPlayerFactory.buildPlayer(),
        defaultScope = defaultScope,
      )
    }
  }
}

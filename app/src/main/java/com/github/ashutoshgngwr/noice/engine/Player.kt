package com.github.ashutoshgngwr.noice.engine

import android.util.Log
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * A media player implementation that plays [Sound]s. This is a base class that implements how
 * [sound segments][com.trynoice.api.client.models.SoundSegment] are queued in the playlist,
 * delegating the actual playback implementation to its subclasses.
 *
 * A [Player] transitions through various [PlaybackState]s during its lifecycle.
 * [PlaybackState.STOPPED] is a terminal state; a [Player] cannot be re-used once it reaches
 * [PlaybackState.STOPPED] state.
 */
abstract class Player protected constructor() {

  /**
   * Audio attributes for playing the audio locally on the host device. A [Player] implementation
   * must adapt its playback when attributes are updated.
   */
  abstract var audioAttributes: AudioAttributesCompat

  /**
   * Id of the sound being played.
   */
  protected abstract val soundId: String

  /**
   * [SoundRepository] instance for querying sound metadata.
   */
  protected abstract val soundRepository: SoundRepository

  /**
   * An external coroutine scope to perform long running background tasks.
   */
  protected abstract val defaultScope: CoroutineScope

  /**
   * A listener for [PlaybackState] and player volume updates.
   */
  protected abstract val playbackListener: PlaybackListener

  protected var fadeInDuration = Duration.ZERO; private set
  protected var fadeOutDuration = Duration.ZERO; private set
  protected var sound: Sound? = null; private set
  private var isPremiumSegmentsEnabled = false
  private var volume = DEFAULT_VOLUME
  private var segments = emptyList<Segment>()
  private var currentSegment: Segment? = null
  private var playbackState = PlaybackState.IDLE

  /**
   * Sets fade duration for fading-in sounds when the playback starts.
   */
  fun setFadeInDuration(duration: Duration) {
    fadeInDuration = duration
  }

  /**
   * Sets fade duration for fading-out sounds when the playback pauses or stops.
   */
  fun setFadeOutDuration(duration: Duration) {
    fadeOutDuration = duration
  }

  /**
   * Sets whether this player is allowed to queue non-free sound segments.
   */
  fun setPremiumSegmentsEnabled(enabled: Boolean) {
    isPremiumSegmentsEnabled = enabled
    recreateSegmentList()
  }

  /**
   * Sets maximum allowed bitrate when streaming segments.
   */
  abstract fun setMaxAudioBitrate(bitrate: Int)

  /**
   * Starts buffering the sound and starts playing its segments once enough data is available.
   */
  fun play() {
    when (playbackState) {
      PlaybackState.STOPPED -> throw IllegalStateException("attempted to re-use a stopped player")
      PlaybackState.BUFFERING, PlaybackState.PLAYING -> Unit
      PlaybackState.FAILED, PlaybackState.IDLE -> loadSoundMetadata()
      else -> playInternal()
    }
  }

  /**
   * Invoked whenever the user issues a play command (on a BUFFERING, PAUSING, PAUSED or STOPPING
   * player). The sub-classes must immediately start/resume playback, and [requestNextSegment] if
   * necessary to do so.
   */
  protected abstract fun playInternal()

  /**
   * Pauses the sound playback. If [immediate] is `true`, the playback is paused immediately.
   * Otherwise, the playback fades out slowly.
   */
  abstract fun pause(immediate: Boolean)

  /**
   * Stops the sound playback with a slow fade-out. The [Player] instance cannot be re-used after
   * once it transitions from [PlaybackState.STOPPING] to [PlaybackState.STOPPED] state.
   */
  abstract fun stop()

  /**
   * Sets the volume of the player.
   *
   * @param volume must be in range [0, [MAX_VOLUME]].
   */
  fun setVolume(volume: Int) {
    require(volume in 0..MAX_VOLUME) { "player volume must be in range [0, ${MAX_VOLUME}]" }
    this.volume = volume
    setVolumeInternal(getScaledVolume())
    notifyPlaybackListener()
  }

  /**
   * The [Player] invokes this with the scaled volume (in range [0, 1]) when [setVolume] is called.
   */
  protected abstract fun setVolumeInternal(volume: Float)

  /**
   * Scales player volume from range [0, [MAX_VOLUME]] to range [0, 1] using a quadratic function.
   */
  protected fun getScaledVolume(): Float {
    // return 0.5f * log(max(1, volume).toFloat(), 5f) // logarithmic
    return (0.04f * volume.toFloat()).pow(2) // quadratic
    // return (0.04f * volume.toFloat()).pow(3) // cubic
  }

  /**
   * Sets the current [playbackState] of this [Player] and notifies the registered
   * [PlaybackListener].
   */
  protected fun setPlaybackState(state: PlaybackState) {
    if (playbackState == state) {
      return
    }

    playbackState = state
    notifyPlaybackListener()
  }

  /**
   * Asynchronously delivers its sub-classes the next segment to play via [onSegmentAvailable]. For
   * contiguous sounds, this invocation is almost immediate. For non-contiguous sounds, the [Player]
   * waits a random duration in range [30, [Sound.maxSilence]] seconds before invoking
   * [onSegmentAvailable]. When [onSegmentAvailable] is invoked, the sub-classes must queue it to be
   * played next.
   */
  protected fun requestNextSegment() {
    defaultScope.launch {
      if (currentSegment != null && sound?.isContiguous == false) {
        val maxSilenceSeconds = requireNotNull(sound?.maxSilence)
        val silenceDuration = Random.nextInt(30, maxSilenceSeconds).toDuration(DurationUnit.SECONDS)
        Log.d(LOG_TAG, "requestNextSegment: adding $silenceDuration silence to non-looping sound.")
        delay(silenceDuration)
      }

      val nextSegment = when {
        currentSegment == null -> segments.random()
        currentSegment?.isBridgeSegment == true -> {
          requireNotNull(segments.find { it.name == currentSegment?.to })
        }
        sound?.isContiguous == true -> {
          val from = requireNotNull(currentSegment)
          val to = segments.random()
          val bridgeName = "${from.name}_${to.name}"
          Segment(
            name = bridgeName,
            path = "${sound?.segmentsBasePath}/${bridgeName}/${bridgeName}.m3u8",
            isBridgeSegment = true,
            from = from.name,
            to = to.name,
          )
        }
        else -> segments.random()
      }

      currentSegment = nextSegment
      Log.d(LOG_TAG, "requestNextSegment: queuing $nextSegment")
      onSegmentAvailable(nextSegment)
    }
  }

  /**
   * Invoked whenever the next segment is available following a [requestNextSegment] invocation from
   * a sub-class.
   *
   * @param segment the sound segment that should be played next.
   */
  protected abstract fun onSegmentAvailable(segment: Segment)

  private fun loadSoundMetadata() {
    setPlaybackState(PlaybackState.BUFFERING)
    defaultScope.launch {
      Log.d(LOG_TAG, "loadSoundMetadata: loading sound metadata")
      val resource = soundRepository.get(soundId)
        .flowOn(Dispatchers.IO)
        .lastOrNull()

      if (resource?.data != null) {
        Log.d(LOG_TAG, "loadSoundMetadata: loaded sound metadata")
        sound = resource.data
        recreateSegmentList()
        if (playbackState == PlaybackState.BUFFERING) {
          playInternal()
        }
      } else {
        Log.w(LOG_TAG, "loadSoundMetadata: failed to load sound metadata", resource?.error)
        setPlaybackState(PlaybackState.FAILED)
      }
    }
  }

  private fun recreateSegmentList() {
    segments = sound?.segments
      ?.filter { isPremiumSegmentsEnabled || it.isFree }
      ?.map { Segment(it.name, "${sound?.segmentsBasePath}/${it.name}/${it.name}.m3u8", false) }
      ?: emptyList()
  }

  private fun notifyPlaybackListener() {
    defaultScope.launch(Dispatchers.Main) {
      playbackListener.onPlaybackUpdated(playbackState, volume)
    }
  }

  companion object {
    private const val LOG_TAG = "Player"
    internal const val DEFAULT_VOLUME = 4
    internal const val MAX_VOLUME = 25
  }

  /**
   * A listener for listening to [PlaybackState] and volume changes of [Player] instance.
   */
  fun interface PlaybackListener {
    fun onPlaybackUpdated(state: PlaybackState, volume: Int)
  }

  /**
   * Internal representation of a [sound segment][com.trynoice.api.client.models.SoundSegment].
   *
   * @param name name of the segment.
   * @param path full path of this segment relative to the `library-manifest.json`.
   * @param isBridgeSegment whether this is bridge segment.
   * @param from if it [isBridgeSegment], then name of the segment that this segment bridges from.
   * @param to if it [isBridgeSegment], then name of the segment that this segment bridges to.
   */
  protected data class Segment(
    val name: String,
    val path: String,
    val isBridgeSegment: Boolean,
    val from: String? = null,
    val to: String? = null,
  )

  /**
   * A factory for [Player] instances.
   */
  interface Factory {

    /**
     * Creates a [Player] instance.
     */
    fun createPlayer(
      soundId: String,
      soundRepository: SoundRepository,
      audioAttributes: AudioAttributesCompat,
      defaultScope: CoroutineScope,
      playbackListener: PlaybackListener
    ): Player
  }
}

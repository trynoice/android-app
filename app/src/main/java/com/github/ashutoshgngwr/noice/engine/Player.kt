package com.github.ashutoshgngwr.noice.engine

import android.util.Log
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.models.Sound
import com.github.ashutoshgngwr.noice.models.SoundSegment
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlin.math.min
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
 *
 * @param soundId id of the sound being played.
 * @param soundRepository a [SoundRepository] instance for querying sound metadata.
 * @param externalScope an external coroutine scope to perform long running background tasks.
 * @param playbackListener a listener for [PlaybackState] and player volume updates.
 */
abstract class Player protected constructor(
  private val soundId: String,
  private var audioBitrate: String,
  private val soundRepository: SoundRepository,
  externalScope: CoroutineScope,
  private val playbackListener: PlaybackListener,
) {

  /**
   * Audio attributes for playing the audio locally on the host device. A [Player] implementation
   * must adapt its playback when attributes are updated.
   */
  abstract var audioAttributes: AudioAttributesCompat

  /**
   * An internal coroutine scope created from the supplied external scope to perform long running
   * background tasks.
   */
  protected val defaultScope = externalScope + SupervisorJob() + CoroutineName("Player#$soundId")

  protected var fadeInDuration = Duration.ZERO; private set
  protected var fadeOutDuration = Duration.ZERO; private set
  protected var sound: Sound? = null; private set
  private var isPremiumSegmentsEnabled = false
  private var volume = DEFAULT_VOLUME
  private var segments = emptyList<SoundSegment>()
  private var currentSegment: SoundSegment? = null
  private var playbackState = PlaybackState.IDLE
  private var retryDelayMillis = MIN_RETRY_DELAY_MILLIS
  private var metadataState = MetadataState.NOT_LOADED

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
    if (enabled == isPremiumSegmentsEnabled) {
      return
    }

    Log.d(LOG_TAG, "setPremiumSegmentsEnabled: setting premium segments enabled to $enabled")
    isPremiumSegmentsEnabled = enabled
    invalidateSegmentQueue()
  }

  /**
   * Sets the audio bitrate for streaming.
   */
  fun setAudioBitrate(bitrate: String) {
    if (bitrate == audioBitrate) {
      return
    }

    Log.d(LOG_TAG, "setAudioBitrate: setting bitrate to $bitrate")
    audioBitrate = bitrate
    invalidateSegmentQueue()
  }

  private fun invalidateSegmentQueue() {
    Log.d(LOG_TAG, "invalidateSegmentQueue: recreating segment list")
    recreateSegmentList()
    if (segments.isEmpty()) {
      Log.d(LOG_TAG, "invalidateSegmentQueue: segments are not available yet")
      return
    }

    currentSegment = null
    Log.d(LOG_TAG, "invalidateSegmentQueue: reset segment queue")
    resetSegmentQueue()
  }

  /**
   * Removes the segments present in queue. The implementations should immediately clear their
   * existing queue and [requestNextSegment].
   */
  protected abstract fun resetSegmentQueue()

  /**
   * Starts buffering the sound and starts playing its segments once enough data is available.
   */
  fun play() {
    when (playbackState) {
      PlaybackState.STOPPED -> throw IllegalStateException("attempted to re-use a stopped player")
      PlaybackState.IDLE -> loadSoundMetadata()
      PlaybackState.BUFFERING, PlaybackState.PLAYING -> Unit
      else -> when (metadataState) {
        MetadataState.NOT_LOADED -> loadSoundMetadata()
        MetadataState.LOADING -> setPlaybackState(PlaybackState.BUFFERING) // may have transitioned to paused state while loading
        MetadataState.LOADED -> playInternal()
      }
    }
  }

  /**
   * Invoked whenever the user issues a play command (on a BUFFERING, PAUSING, PAUSED or STOPPING
   * player). The sub-classes must immediately start/resume playback, and [requestNextSegment] if
   * necessary to do so.
   */
  protected abstract fun playInternal()

  /**
   * Pauses the sound playback. If [immediate] is `true`, the playback pauses immediately.
   * Otherwise, the playback fades out slowly.
   */
  abstract fun pause(immediate: Boolean)

  /**
   * Stops the sound playback with a slow fade-out. The [Player] instance cannot be re-used after
   * once it transitions from [PlaybackState.STOPPING] to [PlaybackState.STOPPED] state. If
   * [immediate] is `true`, the playback stops immediately. Otherwise, the playback fades out
   * slowly.
   */
  abstract fun stop(immediate: Boolean)

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

    // cancel internal scope.
    if (state == PlaybackState.STOPPED) {
      defaultScope.cancel()
    }
  }

  /**
   * Asynchronously delivers its sub-classes the next segment to play via [onSegmentAvailable]. For
   * contiguous sounds, this invocation is almost immediate. For non-contiguous sounds, the [Player]
   * waits a random duration in range [30, `SoundInfo.maxSilence`] seconds before invoking
   * [onSegmentAvailable]. When [onSegmentAvailable] is invoked, the sub-classes must queue it to be
   * played next.
   */
  protected fun requestNextSegment() {
    defaultScope.launch {
      if (currentSegment != null && sound?.info?.isContiguous == false) {
        val maxSilenceSeconds = requireNotNull(sound?.info?.maxSilence)
        val silenceDuration = Random.nextInt(30, maxSilenceSeconds).toDuration(DurationUnit.SECONDS)
        Log.d(LOG_TAG, "requestNextSegment: adding $silenceDuration silence to non-looping sound.")
        delay(silenceDuration)
      }

      val nextSegment = when {
        currentSegment == null -> segments.random()
        currentSegment?.isBridgeSegment == true -> {
          segments.find { it.name == currentSegment?.to } ?: segments.random()
        }
        sound?.info?.isContiguous == true -> {
          val from = requireNotNull(currentSegment)
          segments.filter { it.isBridgeSegment && it.name.startsWith(from.name) }.random()
        }
        else -> segments.random()
      }

      currentSegment = nextSegment
      Log.d(LOG_TAG, "requestNextSegment: queuing $nextSegment")
      onSegmentAvailable("noice://cdn/library/${nextSegment.path(audioBitrate)}")
    }
  }

  /**
   * Invoked whenever the next segment is available following a [requestNextSegment] invocation from
   * a sub-class.
   *
   * @param uri uri of the sound segment that should be played next. The URI format is
   * `noice://cdn/{segmentPath}`, where `segmentPath` is the absolute path of a media file on the
   * CDN.
   */
  protected abstract fun onSegmentAvailable(uri: String)

  private fun loadSoundMetadata() {
    metadataState = MetadataState.LOADING
    setPlaybackState(PlaybackState.BUFFERING)
    defaultScope.launch {
      Log.d(LOG_TAG, "loadSoundMetadata: loading sound metadata for $soundId")
      val resource = soundRepository.get(soundId).lastOrNull()
      if (resource?.data != null) {
        Log.d(LOG_TAG, "loadSoundMetadata: loaded sound metadata for $soundId")
        retryDelayMillis = MIN_RETRY_DELAY_MILLIS
        sound = resource.data
        recreateSegmentList()
        metadataState = MetadataState.LOADED
        if (playbackState == PlaybackState.BUFFERING) {
          Log.d(LOG_TAG, "loadSoundMetadata: starting playback")
          playInternal()
        }
      } else {
        Log.w(LOG_TAG, "loadSoundMetadata: failed to load sound metadata", resource?.error)
        retryDelayMillis.toDuration(DurationUnit.MILLISECONDS)
          .also { Log.i(LOG_TAG, "loadSoundMetadata: retrying in $it") }

        delay(retryDelayMillis)
        retryDelayMillis = min(retryDelayMillis * 2, MAX_RETRY_DELAY_MILLIS)
        loadSoundMetadata()
      }
    }
  }

  private fun recreateSegmentList() {
    segments = sound?.segments
      ?.filter { isPremiumSegmentsEnabled || it.isFree }
      ?: emptyList()
  }

  private fun notifyPlaybackListener() {
    playbackListener.onPlaybackUpdated(playbackState, volume)
  }

  companion object {
    private const val LOG_TAG = "Player"
    private const val MIN_RETRY_DELAY_MILLIS = 1 * 1000L
    private const val MAX_RETRY_DELAY_MILLIS = 30 * 1000L
    internal const val DEFAULT_VOLUME = 20
    internal const val MAX_VOLUME = 25
  }

  /**
   * A listener for listening to [PlaybackState] and volume changes of [Player] instance.
   */
  fun interface PlaybackListener {
    fun onPlaybackUpdated(state: PlaybackState, volume: Int)
  }

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
      audioBitrate: String,
      audioAttributes: AudioAttributesCompat,
      defaultScope: CoroutineScope,
      playbackListener: PlaybackListener
    ): Player
  }

  private enum class MetadataState {
    NOT_LOADED, LOADING, LOADED,
  }
}

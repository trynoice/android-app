package com.github.ashutoshgngwr.noice.engine

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.engine.exoplayer.CdnSoundDataSource
import com.github.ashutoshgngwr.noice.engine.exoplayer.CdnSoundLoadErrorHandlingPolicy
import com.github.ashutoshgngwr.noice.model.PlayerState
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.trynoice.api.client.NoiceApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * [PlayerManager] manages [Player]s for all sounds. It manages Android's audio focus internally.
 */
class PlayerManager(
  private val context: Context,
  private val apiClient: NoiceApiClient,
  private val soundRepository: SoundRepository,
  private var audioAttributes: AudioAttributesCompat,
  private val analyticsProvider: AnalyticsProvider,
  private val defaultScope: CoroutineScope,
  private val playbackListener: PlaybackListener,
) : AudioFocusManager.Listener {

  private var fadeInDuration = Duration.ZERO
  private var fadeOutDuration = Duration.ZERO
  private var maxAudioBitrate = 0
  private var isPremiumSegmentsEnabled = false
  private var playerFactory: Player.Factory = buildLocalPlayerFactory()
  private var audioFocusManager: AudioFocusManager =
    DefaultAudioFocusManager(context, audioAttributes, this)

  private var stopOnIdleJob: Job? = null
  private var scheduledStopJob: Job? = null

  private val players = ConcurrentHashMap<String, Player>()
  private val _playerStates = ConcurrentHashMap<String, PlayerState>()

  /**
   * The [PlayerState]s containing [PlaybackState]s and volumes of currently active sounds in the
   * manager.
   */
  internal val playerStates: Array<PlayerState>
    get() = _playerStates.values.toTypedArray()

  /**
   * The aggregate [PlaybackState] of the [PlayerManager] depending on the individual playback
   * states of the currently active sounds.
   */
  internal val playbackState: PlaybackState
    get() {
      val playbackStates = this._playerStates.values.map { it.playbackState }
      return when {
        playbackStates.isEmpty() -> PlaybackState.STOPPED
        playbackStates.all { it == PlaybackState.STOPPING } -> PlaybackState.STOPPING
        playbackStates.all { it == PlaybackState.PAUSED } -> PlaybackState.PAUSED
        playbackStates.all { it == PlaybackState.PAUSING } -> PlaybackState.PAUSING
        else -> PlaybackState.PLAYING
      }
    }

  override fun onAudioFocusGained() {
    resume()
  }

  override fun onAudioFocusLost(transient: Boolean) {
    if (playbackState.oneOf(PlaybackState.PAUSED, PlaybackState.STOPPED)) {
      return
    }

    if (transient) {
      pauseIndefinitely(true)
    } else {
      pause(true)
    }
  }

  /**
   * Starts playing the sound with the given [soundId]. The playback may be delayed until the
   * Android system grants us the audio focus.
   */
  fun play(soundId: String) {
    val player = players.getOrPut(soundId) {
      _playerStates.putIfAbsent(soundId, PlayerState(soundId, Player.DEFAULT_VOLUME))
      playerFactory.createPlayer(
        soundId,
        soundRepository,
        audioAttributes,
        defaultScope,
        buildPlayerPlaybackListener(soundId)
      ).apply {
        setFadeInDuration(fadeInDuration)
        setFadeOutDuration(fadeOutDuration)
        setPremiumSegmentsEnabled(isPremiumSegmentsEnabled)
        setMaxAudioBitrate(maxAudioBitrate)
      }
    }

    if (audioFocusManager.hasFocus()) {
      player.play()
    } else {
      audioFocusManager.requestFocus()
    }

    notifyPlaybackListener()
    analyticsProvider.logPlayerStartEvent(soundId)
  }

  private fun buildPlayerPlaybackListener(soundId: String): Player.PlaybackListener {
    return Player.PlaybackListener { playbackState, volume ->
      Log.d(LOG_TAG, "Player.PlaybackListener: id=$soundId state=$playbackState volume=$volume")
      _playerStates[soundId] = PlayerState(soundId, volume, playbackState)
      if (playbackState == PlaybackState.STOPPED) {
        players.remove(soundId)
        _playerStates.remove(soundId)
        analyticsProvider.logPlayerStopEvent(soundId)
      }

      if (players.isEmpty()) { // playback has stopped
        audioFocusManager.abandonFocus()
      }

      notifyPlaybackListener()
    }
  }

  /**
   * Begins stopping the sound with the given [soundId] and releases its resources on completion.
   * No-op if the sound corresponding to the given [soundId] isn't active.
   */
  fun stop(soundId: String) {
    players[soundId]?.stop(false)
  }

  /**
   * Begins pausing all active sounds. It doesn't release any underlying resources and preserves
   * currently active sounds for resuming at a later stage. If [immediate] is true, the playback
   * stops immediately. Otherwise, it slowly fades out.
   *
   * If the playback isn't resumed within the next 5 minutes, the [PlayerManager] stops itself.
   */
  fun pause(immediate: Boolean = false) {
    pauseIndefinitely(immediate)
    stopOnIdleJob = defaultScope.launch {
      delay(5.minutes)
      stop(true)
    }
  }

  private fun pauseIndefinitely(immediate: Boolean) {
    stopOnIdleJob?.cancel()
    players.forEach { it.value.pause(immediate) }
  }

  /**
   * Resumes all sounds that were active when the playback [pause]d. The playback may be delayed
   * until the Android system grants us the audio focus.
   */
  fun resume() {
    stopOnIdleJob?.cancel()
    if (audioFocusManager.hasFocus()) {
      players.forEach { it.value.play() }
    } else {
      audioFocusManager.requestFocus()
    }
  }

  /**
   * Begins stopping all sounds and releases their underlying resources on completion.
   */
  fun stop(immediate: Boolean) {
    stopOnIdleJob?.cancel()
    players.values.forEach { it.stop(immediate) }
  }

  /**
   * Sets the [volume] for the sound with the given [soundId].
   */
  fun setVolume(soundId: String, volume: Int) {
    players[soundId]?.setVolume(volume)
  }

  /**
   * Starts playing the sounds and sets their volumes as specified in the given [preset].
   */
  fun play(preset: Preset) {
    // stop players that are not present in preset state
    players.keys
      .subtract(preset.playerStates.map { it.soundId }.toSet())
      .forEach(this::stop)

    // load states from Preset to manager's state
    preset.playerStates.forEach {
      play(it.soundId)
      setVolume(it.soundId, it.volume)
    }
  }

  /**
   * Schedules an automatic stop callback for the [PlayerManager] at the given [atMillis].
   *
   * @param atMillis UNIX timestamp in milliseconds when the [PlayerManager] should stop.
   */
  fun scheduleStop(atMillis: Long) {
    scheduledStopJob = defaultScope.launch {
      delay(atMillis - System.currentTimeMillis())
      stop(false)
    }
  }

  /**
   * Clears the automatic stop callback that was scheduled. No-op if the stop callback wasn't
   * scheduled.
   */
  fun clearStopSchedule() {
    scheduledStopJob?.cancel()
  }

  /**
   * Updates the [audioAttributes] to use for the playback. [audioAttributes] can be updated while
   * the playback is on-going. Although, the playback might pause for a while as we wait for the
   * Android system to grant us the audio focus for the updated [audioAttributes].
   */
  fun setAudioAttributes(audioAttributes: AudioAttributesCompat) {
    this.audioAttributes = audioAttributes
    val wasPlaying = !playbackState.oneOf(PlaybackState.PAUSED, PlaybackState.STOPPED)
    if (audioFocusManager.hasFocus()) {
      pauseIndefinitely(true)
      audioFocusManager.abandonFocus()
    }

    audioFocusManager.setAttributes(audioAttributes)
    players.values.forEach { it.audioAttributes = audioAttributes }
    if (wasPlaying) {
      audioFocusManager.requestFocus()
    }
  }

  /**
   * Enable or disable audio focus management. If disabled, the [PlayerManager] stops requesting
   * audio focus from the Android system.
   */
  fun setAudioFocusManagementEnabled(enabled: Boolean) {
    val oldManager = audioFocusManager
    if (enabled && audioFocusManager !is DefaultAudioFocusManager) {
      audioFocusManager = DefaultAudioFocusManager(context, audioAttributes, this)
    } else if (!enabled && audioFocusManager !is NoopAudioFocusManager) {
      audioFocusManager = NoopAudioFocusManager(this)
    }

    if (oldManager != audioFocusManager) {
      val wasPlaying = !playbackState.oneOf(PlaybackState.PAUSED, PlaybackState.STOPPED)
      pauseIndefinitely(true)
      oldManager.abandonFocus()
      if (wasPlaying) {
        audioFocusManager.requestFocus()
      }
    }
  }

  /**
   * Sets the duration to use for fading in sounds.
   */
  fun setFadeInDuration(duration: Duration) {
    fadeInDuration = duration
    players.values.forEach { it.setFadeInDuration(duration) }
  }

  /**
   * Sets the duration to use for fading out sounds.
   */
  fun setFadeOutDuration(duration: Duration) {
    fadeOutDuration = duration
    players.values.forEach { it.setFadeOutDuration(duration) }
  }

  /**
   * Sets the max audio bitrate to use for streaming sounds.
   */
  fun setMaxAudioBitrate(bitrate: Int) {
    maxAudioBitrate = bitrate
    players.values.forEach { it.setMaxAudioBitrate(bitrate) }
  }

  /**
   * Sets whether sounds should try to play premium segments.
   */
  fun setPremiumSegmentsEnabled(enabled: Boolean) {
    isPremiumSegmentsEnabled = enabled
    players.values.forEach { it.setPremiumSegmentsEnabled(enabled) }
  }

  private fun buildLocalPlayerFactory(): Player.Factory {
    return LocalPlayer.Factory(
      context,
      HlsMediaSource.Factory(CdnSoundDataSource.Factory(apiClient))
        .setLoadErrorHandlingPolicy(CdnSoundLoadErrorHandlingPolicy()),
    )
  }

  private fun notifyPlaybackListener() {
    playbackListener.onPlaybackUpdate(playbackState, playerStates)
  }

  companion object {
    private const val LOG_TAG = "PlayerManager"

    internal const val PRESET_SKIP_DIRECTION_NEXT = 1
    internal const val PRESET_SKIP_DIRECTION_PREV = -1

    internal val DEFAULT_AUDIO_ATTRIBUTES: AudioAttributesCompat by lazy {
      AudioAttributesCompat.Builder()
        .setContentType(AudioAttributesCompat.CONTENT_TYPE_MOVIE)
        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
        .setUsage(AudioAttributesCompat.USAGE_MEDIA)
        .build()
    }

    internal val ALARM_AUDIO_ATTRIBUTES: AudioAttributesCompat by lazy {
      AudioAttributesCompat.Builder()
        .setContentType(AudioAttributesCompat.CONTENT_TYPE_MOVIE)
        .setLegacyStreamType(AudioManager.STREAM_ALARM)
        .setUsage(AudioAttributesCompat.USAGE_ALARM)
        .build()
    }
  }

  fun interface PlaybackListener {
    fun onPlaybackUpdate(playerManagerState: PlaybackState, playerStates: Array<PlayerState>)
  }
}

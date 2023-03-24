package com.github.ashutoshgngwr.noice.engine

import android.media.AudioManager
import android.util.Log
import androidx.media.AudioAttributesCompat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.properties.Delegates.observable
import kotlin.time.Duration

/**
 * [SoundPlayerManager] is responsible for managing the state and lifecycle of [SoundPlayer]
 * instances for each and every sound. The class provides methods to set fade-in and fade-out
 * durations, enable/disable premium segments, set the audio bitrate, and adjust the volumes of all
 * sound players. It also provides methods to play, stop and set volumes of individual sounds, play
 * presets, and pause, resume and stop all sounds.
 *
 * [SoundPlayerManager.State] enum represents playback states of the sound player manager. The
 * manager initialises in [SoundPlayerManager.State.STOPPED] state. There's no terminal state in
 * manager's lifecycle. [SoundPlayerManager.State.PAUSING] and [SoundPlayerManager.State.STOPPING]
 * are special states that the manager temporarily assumes to indicate that all sounds are either in
 * pausing or in stopping states.
 */
class SoundPlayerManager(
  private var soundPlayerFactory: SoundPlayer.Factory,
  private val audioFocusManager: AudioFocusManager,
  private val listener: Listener,
) : AudioFocusManager.Listener {

  private var fadeInDuration = Duration.ZERO
  private var fadeOutDuration = Duration.ZERO
  private var isPremiumSegmentsEnabled = false
  private var audioBitrate = "128k"
  private var audioAttrs = DEFAULT_AUDIO_ATTRIBUTES
  private var volume = 1F
  private var shouldResumeOnFocusGain = false

  private val soundPlayers = ConcurrentHashMap<String, SoundPlayer>()
  private val soundPlayerVolumes = ConcurrentHashMap<String, Float>()

  var state by observable(State.STOPPED) { _, oldValue, newValue ->
    if (oldValue != newValue) listener.onSoundPlayerManagerStateChange(newValue)
  }
    private set

  init {
    audioFocusManager.setAudioAttributes(audioAttrs)
    audioFocusManager.setListener(this)
  }

  override fun onAudioFocusGained() {
    if (shouldResumeOnFocusGain) {
      shouldResumeOnFocusGain = false
      resume()
    }
  }

  override fun onAudioFocusLost(transient: Boolean) {
    if (state == State.PAUSED || state == State.STOPPED) {
      return
    }

    pause(true)
    shouldResumeOnFocusGain = transient
  }

  /**
   * Sets the duration for fading in sounds.
   */
  fun setFadeInDuration(duration: Duration) {
    fadeInDuration = duration
    soundPlayers.values.forEach { it.setFadeInDuration(duration) }
  }

  /**
   * Sets the duration for fading out sounds.
   */
  fun setFadeOutDuration(duration: Duration) {
    fadeOutDuration = duration
    soundPlayers.values.forEach { it.setFadeOutDuration(duration) }
  }

  /**
   * Sets the premium segments enabled flag of all future [SoundPlayer] instances created by the
   * factory, and updates the flag of existing [SoundPlayer] instances.
   */
  fun setPremiumSegmentsEnabled(enabled: Boolean) {
    if (enabled == isPremiumSegmentsEnabled) {
      return
    }

    isPremiumSegmentsEnabled = enabled
    soundPlayers.values.forEach { it.setPremiumSegmentsEnabled(enabled) }
  }

  /**
   * Sets the audio bitrate for streaming sounds.
   *
   * @param bitrate acceptable values are `128k`, `192k`, `256k` and `320k`.
   */
  fun setAudioBitrate(bitrate: String) {
    if (bitrate == audioBitrate) {
      return
    }

    audioBitrate = bitrate
    soundPlayers.values.forEach { it.setAudioBitrate(bitrate) }
  }

  /**
   * Updates the [AudioAttributesCompat] that the sounds use for playback.
   */
  fun setAudioAttributes(attrs: AudioAttributesCompat) {
    if (attrs == audioAttrs) {
      return
    }

    audioAttrs = attrs
    audioFocusManager.setAudioAttributes(attrs)
    soundPlayers.values.forEach { it.setAudioAttributes(attrs) }
  }

  /**
   * Sets the [SoundPlayer.Factory] instance of the [SoundPlayerManager], and recreates all existing
   * [SoundPlayer] instances with the new factory.
   */
  fun setSoundPlayerFactory(factory: SoundPlayer.Factory) {
    if (factory == soundPlayerFactory) {
      return
    }

    soundPlayerFactory = factory
    val soundIds = soundPlayers.keys.filterNot { soundId ->
      val playerState = soundPlayers[soundId]?.state
      playerState == SoundPlayer.State.STOPPING || playerState == SoundPlayer.State.STOPPED
    }

    val pausedSoundIds = soundIds.filter { soundId ->
      val playerState = soundPlayers[soundId]?.state
      playerState == SoundPlayer.State.PAUSING || playerState == SoundPlayer.State.PAUSED
    }.toHashSet()

    stop(true)
    soundPlayers.clear()
    soundIds.forEach { soundId ->
      if (soundId in pausedSoundIds) {
        initSoundPlayer(soundId)
        listener.onSoundStateChange(soundId, soundPlayers.getValue(soundId).state)
      } else {
        playSound(soundId)
      }
    }

    reconcileState()
  }

  /**
   * Sets a global multiplier used to scale individual volumes of all sounds.
   *
   * @param volume must be >= 0 and <= 1.
   * @throws IllegalArgumentException if the volume is not within the accepted range.
   */
  fun setVolume(volume: Float) {
    require(volume in 0F..1F) { "volume must be in range [0, 1]" }
    this.volume = volume
    soundPlayers.forEach { (soundId, player) ->
      player.setVolume(volume * (soundPlayerVolumes[soundId] ?: 1F))
    }

    listener.onSoundPlayerManagerVolumeChange(volume)
  }

  /**
   * Sets the volume of a specific sound identified by the [soundId] parameter.
   *
   * @param volume must be >= 0 and <= 1.
   * @throws IllegalArgumentException if the volume is not within the accepted range.
   */
  fun setSoundVolume(soundId: String, volume: Float) {
    require(volume in 0F..1F) { "volume must be in range [0, 1]" }
    soundPlayerVolumes[soundId] = volume
    soundPlayers[soundId]?.setVolume(this.volume * volume)
    listener.onSoundVolumeChange(soundId, volume)
  }

  /**
   * Plays the sound identified by the [soundId] parameter. It also resumes all sounds if the
   * [SoundPlayerManager] is in the [State.PAUSED].
   */
  fun playSound(soundId: String) {
    initSoundPlayer(soundId)
    val player = soundPlayers.getValue(soundId)
    listener.onSoundStateChange(soundId, player.state)
    if (!audioFocusManager.hasFocus || state == State.PAUSING || state == State.PAUSED) {
      resume()
    } else {
      player.play()
    }
  }

  /**
   * Stops the sound identified by the [soundId] parameter.
   */
  fun stopSound(soundId: String) {
    soundPlayers[soundId]?.stop(false)
  }

  /**
   * Stops all sounds immediately or with a fade-out effect.
   *
   * @param immediate whether the stop should be immediate or if the sounds should perform a
   * fade-out effect before stopping.
   */
  fun stop(immediate: Boolean) {
    shouldResumeOnFocusGain = false
    soundPlayers.values.forEach { it.stop(immediate) }
  }

  /**
   * Pauses all sounds immediately or with a fade-out effect.
   *
   * @param immediate whether the pause should be immediate or if the sounds should perform a
   * fade-out effect before pausing.
   */
  fun pause(immediate: Boolean) {
    shouldResumeOnFocusGain = false
    val isManagerStopping = this.state == State.STOPPING
    soundPlayers.values.forEach { player ->
      // If all sounds are stopping, we should transition to pausing state. If only some sounds are
      // stopping, we should exempt them from pausing.
      if (player.state != SoundPlayer.State.STOPPING || isManagerStopping) {
        player.pause(immediate)
      }
    }
  }

  /**
   * Resumes all sounds that are in [SoundPlayer.State.PAUSED].
   */
  fun resume() {
    if (audioFocusManager.hasFocus) {
      soundPlayers.values.forEach { it.play() }
    } else {
      shouldResumeOnFocusGain = true
      audioFocusManager.requestFocus()
      reconcileState()
    }
  }

  /**
   * Plays a set of sounds identified by keys in the [soundStates] map and sets their volumes to the
   * values in the [soundStates] map, stopping the ones that are not in the [soundStates].
   *
   * @param soundStates a map of sound ids to their desired volumes.
   */
  fun playPreset(soundStates: SortedMap<String, Float>) {
    soundPlayers.keys
      .subtract(soundStates.keys)
      .forEach(this::stopSound)

    soundStates.forEach { (soundId, volume) ->
      setSoundVolume(soundId, volume)
      if (soundPlayers[soundId]?.state != SoundPlayer.State.PLAYING) {
        playSound(soundId)
      }
    }
  }

  /**
   * @return a map of ids of the currently active (buffering, playing, pausing or paused) sounds and
   * their corresponding volumes.
   */
  fun getCurrentPreset(): SortedMap<String, Float> {
    return soundPlayers
      .filterValues { state == State.STOPPING || (it.state != SoundPlayer.State.STOPPING && it.state != SoundPlayer.State.STOPPED) }
      .mapValues { soundPlayerVolumes[it.key] ?: 1F }
      .toSortedMap()
  }

  private fun initSoundPlayer(soundId: String) {
    if (soundPlayers.containsKey(soundId) && soundPlayers[soundId]?.state != SoundPlayer.State.STOPPED) {
      return // a sound player already exists and is not in its terminal state.
    }

    soundPlayers[soundId] = soundPlayerFactory.buildPlayer(soundId).also { p ->
      p.setFadeInDuration(fadeInDuration)
      p.setFadeOutDuration(fadeOutDuration)
      p.setPremiumSegmentsEnabled(isPremiumSegmentsEnabled)
      p.setAudioBitrate(audioBitrate)
      p.setAudioAttributes(audioAttrs)
      p.setVolume(volume * (soundPlayerVolumes[soundId] ?: 1F))
      p.setStateChangeListener { onSoundPlayerStateChange(soundId, it) }
    }
  }

  private fun onSoundPlayerStateChange(soundId: String, state: SoundPlayer.State) {
    Log.d(LOG_TAG, "onSoundPlayerStateChange: $soundId=$state")
    if (state == SoundPlayer.State.STOPPED) {
      soundPlayers.remove(soundId)
    }

    if (soundPlayers.isEmpty()) {
      audioFocusManager.abandonFocus()
    }

    reconcileState()
    listener.onSoundStateChange(soundId, state)
  }

  private fun reconcileState() {
    val soundPlayerStates = soundPlayers.values.map { it.state }
    state = when {
      soundPlayerStates.isEmpty() -> State.STOPPED
      soundPlayerStates.all { it == SoundPlayer.State.STOPPING } -> State.STOPPING
      soundPlayerStates.all { it == SoundPlayer.State.PAUSED } -> State.PAUSED
      // some players may be stopping during when all sounds are paused.
      soundPlayerStates.all { it == SoundPlayer.State.PAUSING || it == SoundPlayer.State.PAUSED || it == SoundPlayer.State.STOPPING } -> State.PAUSING
      else -> State.PLAYING
    }
  }

  companion object {
    private const val LOG_TAG = "SoundPlayerManager"

    /**
     * Audio attributes that the [SoundPlayerManager] should use to perform its playback on the
     * music audio stream.
     */
    val DEFAULT_AUDIO_ATTRIBUTES: AudioAttributesCompat = AudioAttributesCompat.Builder()
      .setContentType(AudioAttributesCompat.CONTENT_TYPE_MOVIE)
      .setLegacyStreamType(AudioManager.STREAM_MUSIC)
      .setUsage(AudioAttributesCompat.USAGE_MEDIA)
      .build()

    /**
     * Audio attributes that the [SoundPlayerManager] should use to perform its playback on the
     * alarm audio stream.
     */
    val ALARM_AUDIO_ATTRIBUTES: AudioAttributesCompat = AudioAttributesCompat.Builder()
      .setContentType(AudioAttributesCompat.CONTENT_TYPE_MOVIE)
      .setLegacyStreamType(AudioManager.STREAM_ALARM)
      .setUsage(AudioAttributesCompat.USAGE_ALARM)
      .build()
  }

  /**
   * Represents the current playback state of the [SoundPlayerManager].
   */
  enum class State {
    /**
     * At least one sound in the manager is currently in buffering or playing state.
     */
    PLAYING,

    /**
     * All sounds in the manager are currently in pausing state.
     */
    PAUSING,

    /**
     * All sounds in the manager are currently in paused state.
     */
    PAUSED,

    /**
     * All sounds in the manager are currently in stopping state.
     */
    STOPPING,

    /**
     * All sounds in the manager are currently in stopped state.
     */
    STOPPED,
  }

  /**
   * A listener interface for observing changes in the playback state of a [SoundPlayerManager] and
   * the playback state and volume [SoundPlayer]s it manages.
   */
  interface Listener {

    /**
     * Invoked when the playback state of the sound player manager changes
     */
    fun onSoundPlayerManagerStateChange(state: State)

    /**
     * Invoked when the volume of the sound player manager changes.
     */
    fun onSoundPlayerManagerVolumeChange(volume: Float)

    /**
     * Invoked when the state of a sound player changes.
     */
    fun onSoundStateChange(soundId: String, state: SoundPlayer.State)

    /**
     * Invoked when the volume of a sound player changes.
     */
    fun onSoundVolumeChange(soundId: String, volume: Float)
  }
}

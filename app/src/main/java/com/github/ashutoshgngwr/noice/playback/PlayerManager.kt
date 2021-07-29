package com.github.ashutoshgngwr.noice.playback

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.content.getSystemService
import androidx.core.os.HandlerCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.playback.strategy.LocalPlaybackStrategyFactory
import com.github.ashutoshgngwr.noice.playback.strategy.PlaybackStrategyFactory
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import java.util.concurrent.TimeUnit

typealias PlaybackUpdateListener = (state: Int, players: Map<String, Player>) -> Unit

/**
 * [PlayerManager] is responsible for managing [Player]s end-to-end for all sounds.
 * It manages Android's audio focus implicitly. It also manages Playback routing to
 * cast enabled devices on-demand.
 */
class PlayerManager(private val context: Context, private val mediaSession: MediaSessionCompat) :
  AudioManager.OnAudioFocusChangeListener {

  companion object {
    private val TAG = PlayerManager::class.simpleName
    private val DELAYED_STOP_CALLBACK_TOKEN = "${PlayerManager::javaClass.name}.stop_callback"

    const val SKIP_DIRECTION_PREV = -1
    const val SKIP_DIRECTION_NEXT = 1
  }

  private var state = PlaybackStateCompat.STATE_STOPPED
  private var hasAudioFocus = false
  private var playbackDelayed = false
  private var resumeOnFocusGain = false
  private var playbackUpdateListener: PlaybackUpdateListener? = null

  private lateinit var audioAttributes: AudioAttributesCompat
  private lateinit var audioFocusRequest: AudioFocusRequestCompat
  private lateinit var playbackStrategyFactory: PlaybackStrategyFactory

  private val players = HashMap<String, Player>(Sound.LIBRARY.size)
  private val handler = Handler(Looper.getMainLooper())
  private val presetRepository = PresetRepository.newInstance(context)
  private val settingsRepository = SettingsRepository.newInstance(context)
  private val analyticsProvider = NoiceApplication.of(context).getAnalyticsProvider()
  private val audioManager = requireNotNull(context.getSystemService<AudioManager>())

  private val playbackStateBuilder = PlaybackStateCompat.Builder()
    .setActions(
      PlaybackStateCompat.ACTION_PLAY_PAUSE
        or PlaybackStateCompat.ACTION_PAUSE
        or PlaybackStateCompat.ACTION_STOP
        or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
    )

  private val castAPIProvider = NoiceApplication.of(context)
    .getCastAPIProviderFactory()
    .newInstance(context)
    .apply {
      onSessionBegin {
        Log.d(TAG, "onSessionBegin(): switching playback to CastPlaybackStrategy")
        playbackStrategyFactory = getPlaybackStrategyFactory()
        players.values.forEach { it.updatePlaybackStrategy(playbackStrategyFactory) }
        mediaSession.setPlaybackToRemote(getVolumeProvider())
        analyticsProvider.logCastSessionStartEvent()
      }

      onSessionEnd {
        // onSessionEnded gets called when restarting the activity. So need to ensure that we're not
        // recreating the LocalPlaybackStrategyFactory again because it will cause [PlaybackStrategy]s to be
        // recreated resulting glitches in playback.
        if (playbackStrategyFactory !is LocalPlaybackStrategyFactory) {
          Log.d(TAG, "onSessionEnd(): switching playback to LocalPlaybackStrategy")
          playbackStrategyFactory = LocalPlaybackStrategyFactory(context, audioAttributes)
          players.values.forEach { it.updatePlaybackStrategy(playbackStrategyFactory) }
          mediaSession.setPlaybackToLocal(AudioManager.STREAM_MUSIC)
          analyticsProvider.logCastSessionEndEvent()
        }
      }
    }

  init {
    setAudioUsage(AudioAttributesCompat.USAGE_MEDIA)
    mediaSession.setCallback(object : MediaSessionCompat.Callback() {
      override fun onPlay() = resume()
      override fun onStop() = stop()
      override fun onPause() = pause()
      override fun onSkipToPrevious() = skipPreset(SKIP_DIRECTION_PREV)
      override fun onSkipToNext() = skipPreset(SKIP_DIRECTION_NEXT)
    })
  }

  // implements audio focus change listener
  override fun onAudioFocusChange(focusChange: Int) {
    when (focusChange) {
      AudioManager.AUDIOFOCUS_GAIN -> {
        Log.d(TAG, "onAudioFocusChange(): gained audio focus")
        hasAudioFocus = true
        if (playbackDelayed || resumeOnFocusGain) {
          Log.d(TAG, "onAudioFocusChange(): resuming playback")
          playbackDelayed = false
          resumeOnFocusGain = false
          resume()
        }
      }
      AudioManager.AUDIOFOCUS_LOSS -> {
        Log.d(TAG, "onAudioFocusChange(): permanently lost audio focus, pause and stop playback")
        hasAudioFocus = false
        resumeOnFocusGain = true
        playbackDelayed = false
        pause()
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        Log.d(TAG, "onAudioFocusChange(): temporarily lost audio focus, pause playback")
        hasAudioFocus = false
        resumeOnFocusGain = true
        playbackDelayed = false
        pauseIndefinitely()
      }
    }
  }

  internal fun setAudioUsage(@AudioAttributesCompat.AttributeUsage usage: Int) {
    if (this::audioAttributes.isInitialized && usage == audioAttributes.usage) {
      return
    }

    Log.d(TAG, "setAudioUsage(): usage = $usage")
    audioAttributes = AudioAttributesCompat.Builder()
      .setContentType(AudioAttributesCompat.CONTENT_TYPE_MOVIE)
      .setUsage(usage)
      .build()

    audioFocusRequest = AudioFocusRequestCompat
      .Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
      .setAudioAttributes(audioAttributes)
      .setOnAudioFocusChangeListener(this, handler)
      .setWillPauseWhenDucked(false)
      .build()

    players.values.forEach { it.setAudioAttributes(audioAttributes) }
    if (!this::playbackStrategyFactory.isInitialized || playbackStrategyFactory is LocalPlaybackStrategyFactory) {
      playbackStrategyFactory = LocalPlaybackStrategyFactory(context, audioAttributes)
    }

    if (hasAudioFocus) {
      abandonAudioFocus()
    }

    if (state == PlaybackStateCompat.STATE_PLAYING) {
      requestAudioFocus()
    }
  }

  // creates audio focus request and handles its response
  private fun requestAudioFocus() {
    if (hasAudioFocus) {
      return
    }

    if (settingsRepository.shouldIgnoreAudioFocusChanges()) {
      hasAudioFocus = true
      resume()
      return
    }

    val result = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest)
    Log.d(TAG, "requestAudioFocus(): result - $result")
    when (result) {
      AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
        Log.d(TAG, "requestAudioFocus(): acquire audio focus request is delayed, pause all players")
        playbackDelayed = true
        hasAudioFocus = false
        resumeOnFocusGain = false
        pauseIndefinitely()
      }
      AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
        Log.d(TAG, "requestAudioFocus(): acquire audio focus request failed, pause all players")
        hasAudioFocus = false
        playbackDelayed = false
        resumeOnFocusGain = false
        pause()
      }
      AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
        Log.d(TAG, "requestAudioFocus(): acquire audio focus request granted, resuming playback")
        hasAudioFocus = true
        playbackDelayed = false
        resumeOnFocusGain = false
        resume()
      }
    }
  }

  private fun abandonAudioFocus() {
    hasAudioFocus = false
    playbackDelayed = false
    resumeOnFocusGain = false

    if (settingsRepository.shouldIgnoreAudioFocusChanges()) {
      return
    }

    when (AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)) {
      AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
        Log.w(TAG, "abandonAudioFocus(): abandon audio focus request failed")
      }
      AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
        Log.d(TAG, "abandonAudioFocus(): abandon audio focus request granted")
      }
    }
  }

  /**
   * starts playing a sound. It also creates an audio focus request if we don't have it.
   * Playback won't start immediately if audio focus is not present. We always ensure that we
   * have audio focus before starting the playback.
   */
  fun play(soundKey: String) {
    if (!players.containsKey(soundKey)) {
      players[soundKey] = Player(soundKey, playbackStrategyFactory)
    }

    if (playbackDelayed) {
      // If audio focus is delayed, add this sound to players and it will be played whenever the
      // we get audio focus.
      state = PlaybackStateCompat.STATE_PAUSED
      notifyChanges()
      return
    }

    if (!hasAudioFocus) {
      // if doesn't have audio focus, request and return because a successful audio focus request
      // will start the player.
      requestAudioFocus()
      return
    }

    state = PlaybackStateCompat.STATE_PLAYING
    requireNotNull(players[soundKey]).play()
    notifyChanges()
    analyticsProvider.logPlayerStartEvent(soundKey)
  }

  /**
   * Stops a [Player] and releases underlying resources. It abandons focus if all [Player]s are
   * stopped.
   */
  fun stop(soundKey: String) {
    players[soundKey]?.also {
      it.stop()
      players.remove(soundKey)
      analyticsProvider.logPlayerStopEvent(soundKey)
    }

    if (players.isEmpty()) {
      state = PlaybackStateCompat.STATE_STOPPED
      Log.d(TAG, "stop(sound): no other sound is playing, abandoning audio focus")
      abandonAudioFocus()
    }

    notifyChanges()
  }

  /**
   * Stops all [Player]s and releases underlying resources. Also abandon the audio focus.
   */
  fun stop() {
    state = PlaybackStateCompat.STATE_STOPPED
    players.values.forEach {
      it.stop()
      analyticsProvider.logPlayerStopEvent(it.soundKey)
    }

    players.clear()
    Log.d(TAG, "stop(): abandoning audio focus")
    abandonAudioFocus()
    notifyChanges()
  }

  /**
   * Stops all [Player]s but maintains their state so that these can be resumed at a later stage.
   * It doesn't release any underlying resources.
   */
  private fun pauseIndefinitely() {
    if (players.isEmpty() && state != PlaybackStateCompat.STATE_PLAYING) {
      return
    }

    state = PlaybackStateCompat.STATE_PAUSED
    players.values.forEach {
      it.pause()
      analyticsProvider.logPlayerStopEvent(it.soundKey)
    }

    notifyChanges()
  }

  /**
   * Stops all [Player]s but maintains their state so that these can be resumed. It schedules a
   * delayed callback to release all underlying resources after 5 minutes.
   */
  fun pause() {
    if (players.isEmpty() && state != PlaybackStateCompat.STATE_PLAYING) {
      return
    }

    pauseIndefinitely()
    Log.d(TAG, "pause(): scheduling stop callback")
    handler.removeCallbacksAndMessages(DELAYED_STOP_CALLBACK_TOKEN) // clear previous callbacks
    HandlerCompat.postDelayed(
      handler, this::stop, DELAYED_STOP_CALLBACK_TOKEN, TimeUnit.MINUTES.toMillis(5)
    )
  }

  /**
   * Resumes all [Player]s from the saved state. It requests AudioFocus if not already present.
   */
  fun resume() {
    if (hasAudioFocus) {
      Log.d(TAG, "resume(): removing delayed stop callbacks, if any")
      handler.removeCallbacksAndMessages(DELAYED_STOP_CALLBACK_TOKEN)
      state = PlaybackStateCompat.STATE_PLAYING
      players.values.forEach {
        it.play()
        analyticsProvider.logPlayerStartEvent(it.soundKey)
      }

      notifyChanges()
    } else if (!playbackDelayed) {
      // request audio focus only if audio focus is not delayed from any previous requests
      requestAudioFocus()
    }
  }

  /**
   * Performs cleanup. must be called when final cleanup is required for the instance
   */
  fun cleanup() {
    stop()
    castAPIProvider.clearSessionCallbacks()
  }

  /**
   * Allows clients to subscribe for changes in [Player]s
   * @param listener a lambda that is called on every update
   */
  fun setPlaybackUpdateListener(listener: PlaybackUpdateListener) {
    playbackUpdateListener = listener
  }

  fun playRandomPreset(tag: Sound.Tag? = null, intensity: IntRange = 2 until 6) {
    playPreset(presetRepository.random(tag, intensity))
  }

  fun playPreset(presetID: String) {
    presetRepository.get(presetID)?.also { playPreset(it) }
  }

  fun playPreset(uri: Uri) {
    playPreset(Preset.from(uri))
  }

  /**
   * [playPreset] efficiently loads a given [Preset] to the [PlayerManager]. It attempts to re-use
   * [Player] instances that are both present in its state and requested by [Preset]. It is also
   * superior to calling [play] manually for each [Preset.PlayerState] since that would cause
   * [PlayerManager] to invoke [playbackUpdateListener] with each [Preset.PlayerState]. This method
   * ensures that [playbackUpdateListener] is invoked only once for any given [Preset].
   */
  internal fun playPreset(preset: Preset) {
    // stop players that are not present in preset state
    players.keys.subtract(preset.playerStates.map { it.soundKey }).forEach {
      players.remove(it)?.stop()
      analyticsProvider.logPlayerStopEvent(it)
    }

    // load states from Preset to manager's state
    preset.playerStates.forEach {
      val player: Player
      if (players.contains(it.soundKey)) {
        player = requireNotNull(players[it.soundKey])
      } else {
        player = Player(it.soundKey, playbackStrategyFactory)
        players[it.soundKey] = player
      }

      player.timePeriod = it.timePeriod
      player.setVolume(it.volume)
    }

    // resume PlayerManager to gain audio focus and play everything
    resume()
  }

  fun callPlaybackUpdateListener() {
    playbackUpdateListener?.invoke(state, players)
  }

  private fun notifyChanges() {
    var speed = 0f
    if (state == PlaybackStateCompat.STATE_PLAYING) {
      speed = 1f
    }

    playbackStateBuilder.setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed)
    mediaSession.setPlaybackState(playbackStateBuilder.build())
    callPlaybackUpdateListener()

    if (state == PlaybackStateCompat.STATE_STOPPED) {
      analyticsProvider.logCastSessionEndEvent()
    }
  }

  fun skipPreset(skipDirection: Int) {
    if (skipDirection != SKIP_DIRECTION_PREV && skipDirection != SKIP_DIRECTION_NEXT) {
      throw IllegalArgumentException(
        "'skipDirection' must be one of 'PlayerManager.SKIP_DIRECTION_PREV' or " +
          "'PlayerManager.SKIP_DIRECTION_NEXT'"
      )
    }

    val presets = presetRepository.list()
    val currentPos = presets.indexOf(Preset.from("", players.values))
    if (currentPos < 0) {
      playRandomPreset()
      return
    }

    var nextPresetIndex = currentPos + skipDirection
    if (nextPresetIndex < 0) {
      nextPresetIndex = presets.size - 1
    } else if (nextPresetIndex >= presets.size) {
      nextPresetIndex = 0
    }

    playPreset(presets[nextPresetIndex])
  }

  fun playerCount(): Int {
    return players.size
  }
}

package com.github.ashutoshgngwr.noice.sound

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.util.Log
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.cast.CastSessionManagerListener
import com.github.ashutoshgngwr.noice.sound.player.SoundPlayerFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * [PlaybackManager] is responsible for managing playback end-to-end for all sounds.
 * Its subscribes to [PlaybackControlEvents] to allow clients to control playback.
 * It also manages Android's audio focus implicitly.
 */
class PlaybackManager(private val context: Context) :
  AudioManager.OnAudioFocusChangeListener {

  /**
   * [PlaybackManager] publishes an [UpdateEvent] every time there is update in the
   * status of an underlying playbacks. It is a light weight alternative to subscribing to a list
   * (map) all playbacks which is also published along with it.
   */
  data class UpdateEvent(val state: State)

  enum class State {
    PLAYING, PAUSED, STOPPED
  }

  companion object {
    private val TAG = PlaybackManager::javaClass.name
  }

  private var isPaused = false
  private var hasAudioFocus = false
  private var playbackDelayed = false
  private var resumeOnFocusGain = false

  private val playbacks = HashMap<String, Playback>(Sound.LIBRARY.size)
  private var eventBus = EventBus.getDefault()
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private val audioAttributes = AudioAttributesCompat.Builder()
    .setContentType(AudioAttributesCompat.CONTENT_TYPE_MOVIE)
    .setUsage(AudioAttributesCompat.USAGE_GAME)
    .setLegacyStreamType(AudioManager.STREAM_MUSIC)
    .build()

  private val audioFocusRequest = AudioFocusRequestCompat
    .Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
    .setAudioAttributes(audioAttributes)
    .setOnAudioFocusChangeListener(this, Handler())
    .setWillPauseWhenDucked(false)
    .build()

  private var playerFactory = SoundPlayerFactory.newLocalPlayerFactory(context, audioAttributes)
  private val castSessionManagerListener = object : CastSessionManagerListener() {
    override fun onSessionStarted(session: CastSession, sessionId: String) {
      playerFactory = SoundPlayerFactory.newCastPlayerFactory(
        session,
        context.getString(R.string.cast_namespace__default)
      )
      reloadPlaybacks()
    }

    override fun onSessionEnded(session: CastSession, error: Int) {
      playerFactory = SoundPlayerFactory.newLocalPlayerFactory(context, audioAttributes)
      reloadPlaybacks()
    }

    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
      playerFactory = SoundPlayerFactory.newCastPlayerFactory(
        session,
        context.getString(R.string.cast_namespace__default)
      )
      reloadPlaybacks()
    }
  }

  init {
    eventBus.register(this)
    CastContext.getSharedInstance(context).sessionManager.addSessionManagerListener(
      castSessionManagerListener,
      CastSession::class.java
    )
  }

  // implements audio focus change listener
  override fun onAudioFocusChange(focusChange: Int) {
    when (focusChange) {
      AudioManager.AUDIOFOCUS_GAIN -> {
        Log.d(TAG, "Gained audio focus...")
        hasAudioFocus = true
        if (playbackDelayed || resumeOnFocusGain) {
          Log.d(TAG, "Resume playback after audio focus gain...")
          playbackDelayed = false
          resumeOnFocusGain = false
          resumeAll()
        }
      }
      AudioManager.AUDIOFOCUS_LOSS -> {
        Log.d(TAG, "Permanently lost audio focus! Stop playback...")
        hasAudioFocus = false
        resumeOnFocusGain = true
        playbackDelayed = false
        pauseAll()
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        Log.d(TAG, "Temporarily lost audio focus! Pause playback...")
        hasAudioFocus = false
        resumeOnFocusGain = true
        playbackDelayed = false
        pauseAll()
      }
    }
  }

  // creates audio focus request and handles its response
  private fun requestAudioFocus() {
    if (hasAudioFocus) {
      return
    }

    val result = AudioManagerCompat.requestAudioFocus(audioManager, audioFocusRequest)
    Log.d(TAG, "AudioFocusRequest result: $result")
    when (result) {
      AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
        Log.d(TAG, "Audio focus request was delayed! Pause playback for now.")
        playbackDelayed = true
        hasAudioFocus = false
        resumeOnFocusGain = false
        pauseAll()
      }
      AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
        Log.d(TAG, "Failed to get audio focus! Stop playback...")
        hasAudioFocus = false
        playbackDelayed = false
        resumeOnFocusGain = false
        pauseAll()
      }
      AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
        hasAudioFocus = true
        playbackDelayed = false
        resumeOnFocusGain = false
        resumeAll()
      }
    }
  }

  // publishes update events for clients.
  private fun notifyChanges() {
    eventBus.postSticky(playbacks)
    eventBus.post(UpdateEvent(getState()))
  }

  // starts playing a sound. It also creates an audio focus request if we don't have it.
  // Playback won't start immediately if audio focus is not present. We always ensure that we
  // have audio focus before starting the playback.
  private fun play(sound: Sound) {
    if (!playbacks.containsKey(sound.key)) {
      playbacks[sound.key] = Playback(sound, playerFactory)
    }

    // notify updates before any returns happen
    notifyChanges()
    if (playbackDelayed) {
      // If audio focus is delayed, add this sound to playbacks and it will be played whenever the
      // we get audio focus.
      return
    }

    if (!hasAudioFocus) {
      requestAudioFocus()
      return
    }

    requireNotNull(playbacks[sound.key]).play()
  }

  // stops a playback and releases its resources.
  private fun stop(sound: Sound) {
    requireNotNull(playbacks[sound.key]).apply {
      stop()
    }

    playbacks.remove(sound.key)
    notifyChanges()
  }

  // stops all playbacks without removing their states
  private fun pauseAll() {
    isPaused = true
    playbacks.values.forEach {
      it.pause()
    }

    AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
    notifyChanges()
  }

  // resumes all playbacks from saved state
  private fun resumeAll() {
    isPaused = false
    playbacks.values.forEach {
      play(requireNotNull(Sound.LIBRARY[it.soundKey]))
    }

    notifyChanges()
  }

  /**
   * Subscriber for the [StartPlaybackEvent][PlaybackControlEvents.StartPlaybackEvent].
   */
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun startPlayback(event: PlaybackControlEvents.StartPlaybackEvent) {
    if (event.soundKey == null) {
      resumeAll()
    } else {
      play(requireNotNull(Sound.LIBRARY[event.soundKey]))
    }
  }

  /**
   * Subscriber for the [StopPlaybackEvent][PlaybackControlEvents.StopPlaybackEvent].
   */
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun stopPlayback(event: PlaybackControlEvents.StopPlaybackEvent) {
    if (event.soundKey == null) {
      stopAll()
    } else {
      stop(requireNotNull(Sound.LIBRARY[event.soundKey]))
    }
  }

  /**
   * Subscriber for the [PausePlaybackEvent][PlaybackControlEvents.PausePlaybackEvent].
   */
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun pausePlayback(@Suppress("UNUSED_PARAMETER") ignored: PlaybackControlEvents.PausePlaybackEvent) {
    pauseAll()
  }

  /**
   * Subscriber for the [UpdatePlaybackEvent][PlaybackControlEvents.UpdatePlaybackEvent].
   */
  @Subscribe(threadMode = ThreadMode.MAIN)
  fun updatePlayback(event: PlaybackControlEvents.UpdatePlaybackEvent) {
    if (!playbacks.containsKey(event.playback.soundKey)) {
      return
    }

    requireNotNull(playbacks[event.playback.soundKey]).also {
      it.timePeriod = event.playback.timePeriod
      it.setVolume(event.playback.volume)
    }
  }

  /**
   * Stops all playbacks and releases underlying resources.
   */
  private fun stopAll() {
    isPaused = false
    playbacks.values.forEach {
      it.stop()
    }

    playbacks.clear()
    AudioManagerCompat.abandonAudioFocusRequest(audioManager, audioFocusRequest)
    notifyChanges()
  }

  /**
   * Returns the current state of the playback manager as one of
   * [State.PLAYING], [State.PAUSED] or [State.STOPPED].
   */
  private fun getState(): State {
    if (playbacks.isEmpty()) {
      return State.STOPPED
    }

    if (isPaused) {
      return State.PAUSED
    }

    return State.PLAYING
  }

  /**
   * attempts to recreate underlying player for playbacks using the [playerFactory]
   */
  private fun reloadPlaybacks() {
    playbacks.values.forEach { it.recreatePlayerWithFactory(playerFactory) }
  }

  /**
   * performs cleanup. must be called when final cleanup is required for the instance
   */
  fun release() {
    stopAll()
    eventBus.unregister(this)
    CastContext.getSharedInstance(context).sessionManager.removeSessionManagerListener(
      castSessionManagerListener,
      CastSession::class.java
    )
  }
}

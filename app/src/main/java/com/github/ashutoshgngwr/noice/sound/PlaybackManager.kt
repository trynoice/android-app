package com.github.ashutoshgngwr.noice.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.util.Log
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

class PlaybackManager(private val context: Context) :
  AudioManager.OnAudioFocusChangeListener {

  class UpdateEvent

  companion object {
    val TAG = PlaybackManager::javaClass.name
  }

  private var isPaused = false
  private var hasAudioFocus = false
  private var playbackDelayed = false
  private var resumeOnFocusGain = false

  private val playbacks = HashMap<String, Playback>(Sound.LIBRARY.size)
  private val eventBus = EventBus.getDefault()
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private val audioSessionId = audioManager.generateAudioSessionId()
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

  init {
    eventBus.register(this)
  }

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
        resumeOnFocusGain = false
        playbackDelayed = false
        stopAll()
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
        pauseAll()
      }
      AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
        Log.d(TAG, "Failed to get audio focus! Stop playback...")
        hasAudioFocus = false
        stopAll()
      }
      AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
        hasAudioFocus = true
        resumeAll()
      }
    }
  }

  private fun notifyChanges() {
    eventBus.postSticky(playbacks)
    eventBus.post(UpdateEvent())
  }

  private fun createPlayback(sound: Sound): Playback {
    return Playback(context, sound, audioSessionId, audioAttributes.unwrap() as AudioAttributes)
  }

  private fun play(sound: Sound) {
    if (!playbacks.containsKey(sound.key)) {
      playbacks[sound.key] = createPlayback(sound)
    }

    // notify updates before any returns happen
    notifyChanges()
    if (playbackDelayed || resumeOnFocusGain) {
      // If playback is paused, add this sound to playbacks and it will be played whenever the
      // playback is resumed.
      return
    }

    if (!hasAudioFocus) {
      requestAudioFocus()
      return
    }

    requireNotNull(playbacks[sound.key]).play()
  }

  private fun stop(sound: Sound) {
    requireNotNull(playbacks[sound.key]).apply {
      stop()
      release()
    }

    playbacks.remove(sound.key)
    notifyChanges()
  }

  @Subscribe
  fun startPlayback(event: PlaybackControlEvents.StartPlaybackEvent) {
    if (event.soundKey == null) {
      resumeAll()
    } else {
      play(requireNotNull(Sound.LIBRARY[event.soundKey]))
    }
  }

  @Subscribe
  fun stopPlayback(event: PlaybackControlEvents.StopPlaybackEvent) {
    if (event.soundKey == null) {
      stopAll()
    } else {
      stop(requireNotNull(Sound.LIBRARY[event.soundKey]))
    }
  }

  @Subscribe
  fun updatePlayback(event: PlaybackControlEvents.UpdatePlaybackEvent) {
    if (!playbacks.containsKey(event.playback.soundKey)) {
      return
    }

    requireNotNull(playbacks[event.playback.soundKey]).also {
      it.timePeriod = event.playback.timePeriod
      it.setVolume(event.playback.volume)
    }
  }

  fun stopAll() {
    playbacks.values.forEach {
      it.stop()
      it.release()
    }

    playbacks.clear()
    notifyChanges()
  }

  fun pauseAll() {
    isPaused = true
    playbacks.values.forEach {
      it.stop()
    }

    notifyChanges()
  }

  fun resumeAll() {
    isPaused = false
    playbacks.values.forEach {
      it.play()
    }

    notifyChanges()
  }

  fun isPaused(): Boolean {
    // at least one scheduled playback should exist for manager to be in paused state
    return isPaused && playbacks.size > 0
  }

  fun isPlaying(): Boolean {
    return playbacks.isNotEmpty()
  }
}

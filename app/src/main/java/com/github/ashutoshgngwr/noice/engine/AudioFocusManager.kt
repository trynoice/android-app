package com.github.ashutoshgngwr.noice.engine

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.core.content.getSystemService
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat

/**
 * Interface definition for managing audio focus.
 */
interface AudioFocusManager {

  val listener: Listener

  /**
   * Returns whether the manager currently holds the audio focus.
   */
  fun hasFocus(): Boolean

  /**
   * Requests to acquire the audio focus from the system. On gaining audio focus, it triggers the
   * [Listener.onAudioFocusGained] callback.
   */
  fun requestFocus()

  /**
   * Requests to abandon the audio focus. The [Listener] stops receiving notifications after this
   * request, therefore, clients should stop their media playback before making an abandon request.
   */
  fun abandonFocus()

  /**
   * Updates the audio attributes used for requesting the audio focus. If the manager currently
   * holds the audio focus, clients must abandon it before changing audio attributes.
   */
  fun setAttributes(audioAttributes: AudioAttributesCompat)

  /**
   * Listener for listening to audio focus changes.
   */
  interface Listener {

    /**
     * Invoked when the system grants the audio focus request.
     */
    fun onAudioFocusGained()

    /**
     * Invoked when the audio focus is lost.
     */
    fun onAudioFocusLost(transient: Boolean)
  }
}

/**
 * An [AudioFocusManager] implementation that doesn't do anything.
 */
class NoopAudioFocusManager(override val listener: AudioFocusManager.Listener) : AudioFocusManager {

  private var hasFocus = false

  override fun hasFocus(): Boolean {
    return hasFocus
  }

  override fun requestFocus() {
    hasFocus = true
    listener.onAudioFocusGained()
  }

  override fun abandonFocus() {
    hasFocus = false
  }

  override fun setAttributes(audioAttributes: AudioAttributesCompat) = Unit
}

/**
 * An [AudioFocusManager] implementation that uses [AudioManager] to manage audio focus.
 */
class DefaultAudioFocusManager(
  context: Context,
  audioAttributes: AudioAttributesCompat,
  override val listener: AudioFocusManager.Listener,
) : AudioFocusManager, AudioManager.OnAudioFocusChangeListener {

  private val audioManager: AudioManager = requireNotNull(context.getSystemService())
  private var hasFocus = false
  private var playbackDelayed = false
  private var resumeOnFocusGain = false
  private var focusRequest = buildFocusRequest(audioAttributes)

  override fun onAudioFocusChange(focusChange: Int) {
    when (focusChange) {
      AudioManager.AUDIOFOCUS_GAIN -> {
        Log.i(LOG_TAG, "onAudioFocusChange: gained audio focus")
        hasFocus = true
        if (playbackDelayed || resumeOnFocusGain) {
          Log.i(LOG_TAG, "onAudioFocusChange: resuming delayed playback")
          playbackDelayed = false
          resumeOnFocusGain = false
          listener.onAudioFocusGained()
        }
      }

      AudioManager.AUDIOFOCUS_LOSS -> {
        Log.i(LOG_TAG, "onAudioFocusChange: permanently lost audio focus")
        hasFocus = false
        resumeOnFocusGain = true
        playbackDelayed = false
        listener.onAudioFocusLost(false)
      }

      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        Log.i(LOG_TAG, "onAudioFocusChange: temporarily lost audio focus")
        hasFocus = false
        resumeOnFocusGain = true
        playbackDelayed = false
        listener.onAudioFocusLost(true)
      }
    }
  }

  override fun hasFocus(): Boolean {
    return hasFocus
  }

  override fun requestFocus() {
    if (hasFocus || playbackDelayed) {
      return
    }

    when (AudioManagerCompat.requestAudioFocus(audioManager, focusRequest)) {
      AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
        Log.i(LOG_TAG, "requestAudioFocus: audio focus request is delayed")
        playbackDelayed = true
        hasFocus = false
        resumeOnFocusGain = false
        listener.onAudioFocusLost(true)
      }
      AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
        Log.w(LOG_TAG, "requestAudioFocus: audio focus request failed")
        hasFocus = false
        playbackDelayed = false
        resumeOnFocusGain = false
        listener.onAudioFocusLost(false)
      }
      AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
        Log.i(LOG_TAG, "requestAudioFocus: audio focus request granted")
        hasFocus = true
        playbackDelayed = false
        resumeOnFocusGain = false
        listener.onAudioFocusGained()
      }
    }
  }

  override fun abandonFocus() {
    when (AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest)) {
      AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
        Log.w(LOG_TAG, "abandonAudioFocus: audio focus request failed")
      }
      AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
        Log.i(LOG_TAG, "abandonAudioFocus: audio focus request granted")
        hasFocus = false
        playbackDelayed = false
        resumeOnFocusGain = false
      }
    }
  }

  override fun setAttributes(audioAttributes: AudioAttributesCompat) {
    focusRequest = buildFocusRequest(audioAttributes)
  }

  private fun buildFocusRequest(audioAttributes: AudioAttributesCompat): AudioFocusRequestCompat {
    return AudioFocusRequestCompat
      .Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
      .setAudioAttributes(audioAttributes)
      .setOnAudioFocusChangeListener(this)
      .setWillPauseWhenDucked(false)
      .build()
  }

  companion object {
    private const val LOG_TAG = "AudioFocusManager"
  }
}

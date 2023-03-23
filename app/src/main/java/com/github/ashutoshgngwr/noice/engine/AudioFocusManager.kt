package com.github.ashutoshgngwr.noice.engine

import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.core.content.getSystemService
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat

/**
 * A convenient wrapper to manage audio focus using the [AudioManager] system service.
 */
class AudioFocusManager(context: Context) : AudioManager.OnAudioFocusChangeListener {

  private val audioManager: AudioManager = requireNotNull(context.getSystemService())

  /**
   * Indicates if the [AudioFocusManager] instance currently possesses audio focus.
   */
  var hasFocus = false; private set

  private var playbackDelayed = false
  private var resumeOnFocusGain = false
  private var isDisabled = false
  private var listener: Listener? = null
  private var focusRequest: AudioFocusRequestCompat? = null

  override fun onAudioFocusChange(focusChange: Int) {
    when (focusChange) {
      AudioManager.AUDIOFOCUS_GAIN -> {
        Log.i(LOG_TAG, "onAudioFocusChange: gained audio focus")
        hasFocus = true
        if (playbackDelayed || resumeOnFocusGain) {
          Log.i(LOG_TAG, "onAudioFocusChange: resuming delayed playback")
          playbackDelayed = false
          resumeOnFocusGain = false
          listener?.onAudioFocusGained()
        }
      }

      AudioManager.AUDIOFOCUS_LOSS -> {
        Log.i(LOG_TAG, "onAudioFocusChange: permanently lost audio focus")
        hasFocus = false
        resumeOnFocusGain = true
        playbackDelayed = false
        listener?.onAudioFocusLost(false)
      }

      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        Log.i(LOG_TAG, "onAudioFocusChange: temporarily lost audio focus")
        hasFocus = false
        resumeOnFocusGain = true
        playbackDelayed = false
        listener?.onAudioFocusLost(true)
      }
    }
  }

  /**
   * Registers a [Listener] instance to listen for audio focus changes.
   */
  fun setListener(listener: Listener?) {
    this.listener = listener
  }

  /**
   * Requests to acquire the audio focus from the system. On gaining audio focus, it invokes
   * [Listener.onAudioFocusGained] callback.
   */
  fun requestFocus() {
    if (isDisabled) {
      hasFocus = true
      listener?.onAudioFocusGained()
      return
    }

    if (hasFocus || playbackDelayed) {
      return
    }

    when (AudioManagerCompat.requestAudioFocus(audioManager, requireFocusRequest())) {
      AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
        Log.i(LOG_TAG, "requestFocus: audio focus request is delayed")
        playbackDelayed = true
        hasFocus = false
        resumeOnFocusGain = false
      }
      AudioManager.AUDIOFOCUS_REQUEST_FAILED -> {
        Log.w(LOG_TAG, "requestFocus: audio focus request failed")
        hasFocus = false
        playbackDelayed = false
        resumeOnFocusGain = false
      }
      AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
        Log.i(LOG_TAG, "requestFocus: audio focus request granted")
        hasFocus = true
        playbackDelayed = false
        resumeOnFocusGain = false
        listener?.onAudioFocusGained()
      }
    }
  }

  /**
   * Requests to abandon the audio focus. The [Listener] doesn't receive notifications after this
   * request. Therefore, clients should stop their media playback before making an abandon request.
   */
  fun abandonFocus() {
    if (isDisabled) {
      hasFocus = false
      return
    }

    when (AudioManagerCompat.abandonAudioFocusRequest(audioManager, requireFocusRequest())) {
      AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
        Log.i(LOG_TAG, "abandonFocus: audio focus request granted")
        hasFocus = false
        playbackDelayed = false
        resumeOnFocusGain = false
      }
      else -> Log.w(LOG_TAG, "abandonFocus: audio focus request failed")
    }
  }

  /**
   * Updates the audio attributes used for requesting the audio focus. The manager will abandon
   * existing focus request and request it again using the updated [audioAttributes].
   */
  fun setAudioAttributes(audioAttributes: AudioAttributesCompat) {
    val shouldRequest = hasFocus || playbackDelayed || resumeOnFocusGain
    if (focusRequest != null) { // can't abandon if being set for the first time
      abandonFocus()
      listener?.onAudioFocusLost(true)
    }

    focusRequest = buildFocusRequest(audioAttributes)
    if (shouldRequest) {
      requestFocus()
    }
  }

  /**
   * Disables or enables audio focus management in this manager instance. The manager will abandon
   * existing focus request and request it again if necessary.
   */
  fun setDisabled(disabled: Boolean) {
    if (disabled == isDisabled) {
      return
    }

    if (focusRequest == null) { // audio attributes have not been set yet.
      isDisabled = disabled
      return
    }

    val shouldRequest = hasFocus || playbackDelayed || resumeOnFocusGain
    abandonFocus()
    listener?.onAudioFocusLost(true)
    isDisabled = disabled
    if (shouldRequest) {
      requestFocus()
    }
  }

  private fun buildFocusRequest(audioAttributes: AudioAttributesCompat): AudioFocusRequestCompat {
    return AudioFocusRequestCompat
      .Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
      .setAudioAttributes(audioAttributes)
      .setOnAudioFocusChangeListener(this)
      .setWillPauseWhenDucked(false)
      .build()
  }

  private fun requireFocusRequest(): AudioFocusRequestCompat {
    return requireNotNull(focusRequest) { "must set audio attributes before requesting or abandoning focus" }
  }

  companion object {
    private const val LOG_TAG = "AudioFocusManager"
  }

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

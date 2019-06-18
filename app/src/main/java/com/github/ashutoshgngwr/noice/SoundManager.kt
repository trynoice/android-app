package com.github.ashutoshgngwr.noice

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import androidx.annotation.VisibleForTesting
import com.github.ashutoshgngwr.noice.fragment.PresetFragment
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment.Sound.Companion.LIBRARY
import kotlin.random.Random

class SoundManager(context: Context, audioAttributes: AudioAttributes) {

  class Playback(val sound: SoundLibraryFragment.Sound, val soundId: Int) {
    var volume: Float = 0.2f
    var timePeriod: Int = 60
    var streamId: Int = 0
    var isPlaying: Boolean = false
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  val mSoundPool: SoundPool = SoundPool.Builder()
    .setAudioAttributes(audioAttributes)
    .setMaxStreams(LIBRARY.size)
    .build()

  private val mHandler = Handler()
  private val playbacks = HashMap<String, Playback>(LIBRARY.size)
  private val randomPlaybackCallbacks = HashMap<String, Runnable>(LIBRARY.size)
  private val pauseState = ArrayList<String>(LIBRARY.size)
  private val playbackListeners = ArrayList<OnPlaybackStateChangeListener>()

  var isPlaying: Boolean = false
    private set

  init {
    for (sound in LIBRARY) {
      playbacks[sound.key] = Playback(
        sound,
        mSoundPool.load(context, sound.resId, 1)
      )
    }
  }

  private fun play(playback: Playback) {
    if (!isPlaying && isPaused()) {
      if (!pauseState.contains(playback.sound.key)) {
        pauseState.add(playback.sound.key)
      }
      // UI should notify user that playback is pause,
      // and sound will be played when it is resumed.
      return
    }

    isPlaying = true
    playback.isPlaying = true

    if (playback.sound.isLoopable) {
      playback.streamId = mSoundPool.play(
        playback.soundId,
        playback.volume,
        playback.volume,
        1,
        -1,
        1.0f
      )
    } else {
      // non-loopable sounds should be played at random intervals in defined period
      randomPlaybackCallbacks[playback.sound.key] = object : Runnable {
        override fun run() {
          if (isPlaying) {
            playback.streamId = mSoundPool.play(
              playback.soundId,
              playback.volume,
              playback.volume,
              1,
              0,
              1.0f
            )
          }

          // min delay = 15 secs, max delay = 15 + user defined timePeriod
          mHandler.postDelayed(this, (15 + (Random.nextLong() % playback.timePeriod)) * 1000)
        }
      }
      mHandler.postDelayed(
        randomPlaybackCallbacks[playback.sound.key],
        (15 + (Random.nextLong() % playback.timePeriod)) * 1000
      )
    }
  }

  fun play(soundKey: String) {
    val wasPlaying = isPlaying
    play(playbacks[soundKey]!!)
    notifyPlaybackStateChange(
      if (wasPlaying) {
        OnPlaybackStateChangeListener.STATE_PLAYBACK_UPDATED
      } else {
        OnPlaybackStateChangeListener.STATE_PLAYBACK_STARTED
      }
    )
  }

  private fun stop(playback: Playback) {
    mSoundPool.stop(playback.streamId)
    playback.isPlaying = false
    playback.streamId = 0

    if (!playback.sound.isLoopable) {
      mHandler.removeCallbacks(randomPlaybackCallbacks[playback.sound.key])
      randomPlaybackCallbacks.remove(playback.sound.key)
    }
  }

  fun stop(soundKey: String) {
    stop(playbacks[soundKey]!!)

    // see if all playbacks are stopped
    var isPlaying = false
    for (p in playbacks.values) {
      isPlaying = isPlaying || p.isPlaying
    }

    this.isPlaying = this.isPlaying && isPlaying
    notifyPlaybackStateChange(
      if (this.isPlaying) {
        OnPlaybackStateChangeListener.STATE_PLAYBACK_UPDATED
      } else {
        OnPlaybackStateChangeListener.STATE_PLAYBACK_STOPPED
      }
    )
  }

  fun stopPlayback() {
    isPlaying = false
    for (playback in playbacks.values) {
      if (playback.isPlaying) {
        stop(playback)
      }
    }

    pauseState.clear()
    notifyPlaybackStateChange(OnPlaybackStateChangeListener.STATE_PLAYBACK_STOPPED)
  }

  fun pausePlayback() {
    if (!isPlaying) {
      return
    }

    isPlaying = false
    pauseState.clear()

    for (playback in playbacks.values) {
      if (playback.isPlaying) {
        pauseState.add(playback.sound.key)
        stop(playback)
      }
    }

    notifyPlaybackStateChange(OnPlaybackStateChangeListener.STATE_PLAYBACK_PAUSED)
  }

  fun resumePlayback() {
    if (pauseState.isEmpty()) {
      return
    }

    isPlaying = true

    for (soundKey in pauseState) {
      play(playbacks[soundKey]!!)
    }

    pauseState.clear()
    notifyPlaybackStateChange(OnPlaybackStateChangeListener.STATE_PLAYBACK_RESUMED)
  }

  fun isPlaying(soundKey: String): Boolean {
    return isPlaying && playbacks[soundKey]!!.isPlaying
  }

  fun getVolume(soundKey: String): Int {
    return Math.round(playbacks[soundKey]!!.volume * 20)
  }

  fun setVolume(soundKey: String, volume: Int) {
    val playback = playbacks[soundKey]!!
    playback.volume = volume / 20.0f
    mSoundPool.setVolume(playback.streamId, playback.volume, playback.volume)
    notifyPlaybackStateChange(OnPlaybackStateChangeListener.STATE_PLAYBACK_UPDATED)
  }

  fun getTimePeriod(soundKey: String): Int {
    return playbacks[soundKey]!!.timePeriod
  }

  fun setTimePeriod(soundKey: String, timePeriod: Int) {
    playbacks[soundKey]!!.timePeriod = timePeriod
    notifyPlaybackStateChange(OnPlaybackStateChangeListener.STATE_PLAYBACK_UPDATED)
  }

  fun release() {
    isPlaying = false
    mSoundPool.release()
    notifyPlaybackStateChange(OnPlaybackStateChangeListener.STATE_PLAYBACK_STOPPED)
  }

  fun addOnPlaybackStateChangeListener(listener: OnPlaybackStateChangeListener) {
    playbackListeners.add(listener)
  }

  fun removeOnPlaybackStateChangeListener(listener: OnPlaybackStateChangeListener) {
    playbackListeners.remove(listener)
  }

  fun isPaused(): Boolean {
    return pauseState.isNotEmpty()
  }

  fun getCurrentPreset(): PresetFragment.Preset? {
    if (!isPlaying) {
      return null
    }

    val presetPlaybackStates = ArrayList<PresetFragment.Preset.PresetPlaybackState>()
    for (p in playbacks.values) {
      if (p.isPlaying) {
        presetPlaybackStates.add(
          PresetFragment.Preset.PresetPlaybackState(p.sound.key, p.volume, p.timePeriod)
        )
      }
    }

    return PresetFragment.Preset("", presetPlaybackStates.toTypedArray())
  }

  fun playPreset(preset: PresetFragment.Preset) {
    stopPlayback()

    for (playbackState in preset.playbackStates) {
      val playback = playbacks[playbackState.soundKey]!!
      playback.volume = playbackState.volume
      playback.timePeriod = playbackState.timePeriod
      play(playback)
    }

    notifyPlaybackStateChange(OnPlaybackStateChangeListener.STATE_PLAYBACK_STARTED)
  }

  private fun notifyPlaybackStateChange(playbackState: Int) {
    for (listener in playbackListeners) {
      listener.onPlaybackStateChanged(playbackState)
    }
  }

  interface OnPlaybackStateChangeListener {

    companion object {
      const val STATE_PLAYBACK_STARTED = 0x0
      const val STATE_PLAYBACK_STOPPED = 0x1
      const val STATE_PLAYBACK_PAUSED = 0x2
      const val STATE_PLAYBACK_RESUMED = 0x3
      const val STATE_PLAYBACK_UPDATED = 0x4
    }

    fun onPlaybackStateChanged(playbackState: Int)
  }
}

package com.github.ashutoshgngwr.noice

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.util.SparseArray
import androidx.annotation.VisibleForTesting
import androidx.core.util.set
import androidx.core.util.valueIterator
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
    .setMaxStreams(LIBRARY.size())
    .build()

  private val mHandler = Handler()
  private val playbacks = SparseArray<Playback>(LIBRARY.size())
  private val randomPlaybackCallbacks = SparseArray<Runnable>(LIBRARY.size())
  private val pauseState = ArrayList<Int>(LIBRARY.size())
  private val playbackListeners = ArrayList<OnPlaybackStateChangeListener>()

  var isPlaying: Boolean = false
    private set

  init {
    for (sound in LIBRARY.valueIterator()) {
      playbacks[sound.resId] = Playback(
        sound,
        mSoundPool.load(context, sound.resId, 1)
      )
    }
  }

  private fun play(playback: Playback) {
    if (!isPlaying && isPaused()) {
      if (!pauseState.contains(playback.sound.resId)) {
        pauseState.add(playback.sound.resId)
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
      randomPlaybackCallbacks[playback.sound.resId] = object : Runnable {
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

          // min delay = 10 secs, max delay = 10 + user defined timePeriod
          mHandler.postDelayed(this, (10 + (Random.nextLong() % playback.timePeriod)) * 1000)
        }
      }
      randomPlaybackCallbacks[playback.sound.resId].run()
    }
  }

  fun play(soundResId: Int) {
    val wasPlaying = isPlaying
    play(playbacks[soundResId])
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
      mHandler.removeCallbacks(randomPlaybackCallbacks[playback.sound.resId])
      randomPlaybackCallbacks.delete(playback.sound.resId)
    }
  }

  fun stop(soundResId: Int) {
    stop(playbacks[soundResId])

    // see if all playbacks are stopped
    var isPlaying = false
    for (p in playbacks.valueIterator()) {
      isPlaying = isPlaying || p.isPlaying
    }

    this.isPlaying = this.isPlaying && isPlaying
    notifyPlaybackStateChange(
      if (isPlaying) {
        OnPlaybackStateChangeListener.STATE_PLAYBACK_UPDATED
      } else {
        OnPlaybackStateChangeListener.STATE_PLAYBACK_STOPPED
      }
    )
  }

  fun stopPlayback() {
    isPlaying = false
    for (playback in playbacks.valueIterator()) {
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

    for (playback in playbacks.valueIterator()) {
      if (playback.isPlaying) {
        pauseState.add(playback.sound.resId)
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

    for (soundResId in pauseState) {
      play(playbacks[soundResId])
    }

    pauseState.clear()
    notifyPlaybackStateChange(OnPlaybackStateChangeListener.STATE_PLAYBACK_RESUMED)
  }

  fun isPlaying(soundResId: Int): Boolean {
    return isPlaying && playbacks[soundResId].isPlaying
  }

  fun getVolume(soundResId: Int): Int {
    return Math.round(playbacks[soundResId].volume * 20)
  }

  fun setVolume(soundResId: Int, volume: Int) {
    val playback = playbacks[soundResId]
    playback.volume = volume / 20.0f
    mSoundPool.setVolume(playback.streamId, playback.volume, playback.volume)
    notifyPlaybackStateChange(OnPlaybackStateChangeListener.STATE_PLAYBACK_UPDATED)
  }

  fun getTimePeriod(soundResId: Int): Int {
    return playbacks[soundResId].timePeriod
  }

  fun setTimePeriod(soundResId: Int, timePeriod: Int) {
    playbacks[soundResId].timePeriod = timePeriod
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

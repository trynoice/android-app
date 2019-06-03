package com.github.ashutoshgngwr.noice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.util.SparseArray
import androidx.core.util.set
import androidx.core.util.valueIterator
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment.Sound.Companion.LIBRARY
import kotlin.random.Random

class SoundManager(mediaPlayerService: Context) : AudioManager.OnAudioFocusChangeListener {

  class Playback(val sound: SoundLibraryFragment.Sound, val soundId: Int) {
    var volume: Float = 0.2f
    var timePeriod: Int = 60
    var streamId: Int = 0
    var isPlaying: Boolean = false
  }

  private val mSoundPool = SoundPool.Builder()
    .setAudioAttributes(
      AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
        .setUsage(AudioAttributes.USAGE_GAME)
        .build()
    )
    .setMaxStreams(LIBRARY.size())
    .build()

  private val mHandler = Handler()
  private val playbacks = SparseArray<Playback>(LIBRARY.size())
  private val randomPlaybackCallbacks = SparseArray<Runnable>(LIBRARY.size())
  private val pauseState = ArrayList<Int>(LIBRARY.size())
  private var playbackListener: OnPlaybackStateChangeListener? = null

  var isPlaying: Boolean = false

  init {
    val audioManager = mediaPlayerService.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Suppress("DEPRECATION")
    audioManager.requestAudioFocus(
      this,
      AudioManager.STREAM_MUSIC,
      AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
    )

    for (sound in LIBRARY.valueIterator()) {
      playbacks[sound.resId] = Playback(
        sound,
        mSoundPool.load(mediaPlayerService, sound.resId, 1)
      )
    }
  }

  override fun onAudioFocusChange(focusChange: Int) {
    when (focusChange) {
      AudioManager.AUDIOFOCUS_LOSS -> {
        pausePlayback()
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        pausePlayback()
      }
      AudioManager.AUDIOFOCUS_GAIN -> {
        resumePlayback()
      }
    }
  }

  private fun play(playback: Playback) {
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

          mHandler.postDelayed(this, Random.nextLong() % (playback.timePeriod * 1000))
        }
      }
      randomPlaybackCallbacks[playback.sound.resId].run()
    }
  }

  fun play(soundResId: Int) {
    play(playbacks[soundResId])
    notifyPlaybackStateChange()
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
    notifyPlaybackStateChange()
  }

  fun stop() {
    isPlaying = false
    for (playback in playbacks.valueIterator()) {
      if (playback.isPlaying) {
        stop(playback)
      }
    }

    pauseState.clear()
    notifyPlaybackStateChange()
  }

  fun pausePlayback() {
    isPlaying = false
    pauseState.clear()

    for (playback in playbacks.valueIterator()) {
      if (playback.isPlaying) {
        pauseState.add(playback.sound.resId)
        stop(playback)
      }
    }

    notifyPlaybackStateChange()
  }

  fun resumePlayback() {
    isPlaying = true

    for (soundResId in pauseState) {
      play(playbacks[soundResId])
    }

    pauseState.clear()
    notifyPlaybackStateChange()
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
  }

  fun getTimePeriod(soundResId: Int): Int {
    return playbacks[soundResId].timePeriod
  }

  fun setTimePeriod(soundResId: Int, timePeriod: Int) {
    playbacks[soundResId].timePeriod = timePeriod
  }

  fun release() {
    isPlaying = false
    mSoundPool.release()
    notifyPlaybackStateChange()
  }

  fun setOnPlaybackStateChangeListener(listener: OnPlaybackStateChangeListener?) {
    this.playbackListener = listener
  }

  fun isPaused(): Boolean {
    return pauseState.isNotEmpty()
  }

  private fun notifyPlaybackStateChange() {
    playbackListener?.onPlaybackStateChanged()
  }

  interface OnPlaybackStateChangeListener {
    fun onPlaybackStateChanged()
  }
}

package com.github.ashutoshgngwr.noice.sound

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler

/**
 * Playback manages playback of a single [Sound]. It holds reference to underlying
 * [MediaPlayer][android.media.MediaPlayer] instance along with playback information such
 * as isPlaying, volume and timePeriod.
 */
final class Playback(
  context: Context,
  sound: Sound,
  sessionId: Int,
  audioAttributes: AudioAttributes
) : Runnable {

  companion object {
    const val DEFAULT_VOLUME = 4.0f
    const val MAX_VOLUME = 20.0f
    const val DEFAULT_TIME_PERIOD = 60
    const val MINIMUM_TIME_PERIOD = 20
  }

  var volume = DEFAULT_VOLUME
    private set

  var timePeriod = DEFAULT_TIME_PERIOD - MINIMUM_TIME_PERIOD

  var isPlaying = false
    private set

  private val mediaPlayer = createMediaPlayer(context.assets, sessionId, audioAttributes, sound)
  private val handler = Handler()

  /**
   * Initializes a [MediaPlayer][android.media.MediaPlayer] object with the passed arguments.
   */
  private fun createMediaPlayer(
    assetManager: AssetManager,
    sessionId: Int,
    audioAttributes: AudioAttributes,
    sound: Sound
  ): MediaPlayer {
    assetManager.openFd(sound.key).use { afd ->
      return MediaPlayer()
        .apply {
          isLooping = sound.isLoopable
          audioSessionId = sessionId
          setAudioAttributes(audioAttributes)
          setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
          setVolume(volume / MAX_VOLUME, volume / MAX_VOLUME)
          prepare()
        }
    }
  }

  /**
   * Sets the volume for the playback. It also sets the volume of the underlying
   * [MediaPlayer][android.media.MediaPlayer] instance.
   */
  fun setVolume(volume: Float) {
    this.volume = volume
    mediaPlayer.setVolume(volume / MAX_VOLUME, volume / MAX_VOLUME)
  }

  /**
   * Starts playing the sound. If the sound is not loopable, it also schedules a delayed
   * task to replay the sound. Delay period is randomised with guaranteed
   * [MINIMUM_TIME_PERIOD][MINIMUM_TIME_PERIOD].
   */
  fun play() {
    isPlaying = true
    if (mediaPlayer.isLooping) {
      mediaPlayer.start()
    } else {
      run()
    }
  }

  /**
   * Implements the randomised play callback for non-looping sounds.
   */
  override fun run() {
    if (!isPlaying) {
      return
    }

    mediaPlayer.start()
    handler.postDelayed(this, timePeriod * 1000L)
  }

  /**
   * Stops the playback. If the sound is non-loopable, it also removes the randomised play callback.
   */
  fun stop() {
    isPlaying = false
    if (mediaPlayer.isPlaying) {
      mediaPlayer.stop()
    }

    if (mediaPlayer.isLooping) {
      handler.removeCallbacks(this)
    }
  }
}

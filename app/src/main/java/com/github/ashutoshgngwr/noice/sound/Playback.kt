package com.github.ashutoshgngwr.noice.sound

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import androidx.annotation.VisibleForTesting
import androidx.media.AudioAttributesCompat
import com.google.gson.annotations.Expose
import kotlin.random.Random.Default.nextInt

/**
 * Playback manages playback of a single [Sound]. It holds reference to underlying
 * [MediaPlayer][android.media.MediaPlayer] instance along with playback information such
 * as isPlaying, volume and timePeriod.
 */
class Playback(
  context: Context,
  sound: Sound,
  sessionId: Int,
  audioAttributes: AudioAttributesCompat
) : Runnable {

  companion object {
    const val DEFAULT_VOLUME = 4
    const val MAX_VOLUME = 20
    const val DEFAULT_TIME_PERIOD = 30
    private const val MIN_TIME_PERIOD = 30
    const val MAX_TIME_PERIOD = 240
  }

  @Expose
  var volume = DEFAULT_VOLUME
    private set

  @Expose
  var timePeriod = DEFAULT_TIME_PERIOD

  var isPlaying = false
    private set

  @Expose
  val soundKey = sound.key

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  val mediaPlayer = createMediaPlayer(context.assets, sessionId, audioAttributes, sound)

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  val handler = Handler()

  // initializes a MediaPlayer object with the passed arguments.
  private fun createMediaPlayer(
    assetManager: AssetManager,
    sessionId: Int,
    audioAttributes: AudioAttributesCompat,
    sound: Sound
  ): MediaPlayer {
    return MediaPlayer().apply {
      isLooping = sound.isLoopable
      audioSessionId = sessionId
      setAudioAttributes(audioAttributes.unwrap() as AudioAttributes)
      setVolume(volume.toFloat() / MAX_VOLUME, volume.toFloat() / MAX_VOLUME)
      assetManager.openFd("${sound.key}.mp3").use {
        setDataSource(it.fileDescriptor, it.startOffset, it.length)
        prepare()
      }
    }
  }

  /**
   * Sets the volume for the playback. It also sets the volume of the underlying
   * [MediaPlayer][android.media.MediaPlayer] instance.
   */
  fun setVolume(volume: Int) {
    this.volume = volume
    mediaPlayer.setVolume(volume.toFloat() / MAX_VOLUME, volume.toFloat() / MAX_VOLUME)
  }

  /**
   * Starts playing the sound. If the sound is not loopable, it also schedules a delayed
   * task to replay the sound. Delay period is randomised with guaranteed
   * [MIN_TIME_PERIOD][MIN_TIME_PERIOD].
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
    handler.postDelayed(this, (MIN_TIME_PERIOD + nextInt(0, timePeriod)) * 1000L)
  }

  /**
   * Stops the playback. If the sound is non-loopable, it also removes the randomised play callback.
   */
  fun stop() {
    isPlaying = false
    if (mediaPlayer.isPlaying) {
      mediaPlayer.pause()
    }

    if (!mediaPlayer.isLooping) {
      handler.removeCallbacks(this)
    }
  }

  /**
   * Releases resources used by underlying [MediaPlayer][android.media.MediaPlayer] instance.
   */
  fun release() {
    stop()
    mediaPlayer.release()
  }

  /**
   * Custom implementation of equals is required for comparing playback states
   * in comparing saved Presets
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }

    if (javaClass != other?.javaClass) {
      return false
    }

    other as Playback
    return soundKey == other.soundKey && volume == other.volume && timePeriod == other.timePeriod
  }

  /**
   * auto-generated
   */
  override fun hashCode(): Int {
    var result = volume
    result = 31 * result + timePeriod
    result = 31 * result + soundKey.hashCode()
    return result
  }
}

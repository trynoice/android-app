package com.github.ashutoshgngwr.noice.sound.player

/**
 * [SoundPlayer] interface is the generic type used by the Playback class to control the underlying
 * sound resource.
 */
abstract class SoundPlayer {
  /**
   * sets the media volume.
   *
   * @param volume: 0 is min, 1 is max
   */
  abstract fun setVolume(volume: Float)

  /**
   * starts playing the sound
   */
  abstract fun play()

  /**
   * stops playing the sound but does not release the underlying sound resource.
   */
  abstract fun pause()

  /**
   * stops playing the sound and releases the underlying sound resource.
   */
  abstract fun stop()
}

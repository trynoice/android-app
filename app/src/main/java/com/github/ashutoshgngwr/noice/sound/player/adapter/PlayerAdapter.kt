package com.github.ashutoshgngwr.noice.sound.player.adapter

/**
 * [PlayerAdapter] interface is the generic type used by the SoundPlayer class to control the
 * underlying playback mechanism
 */
interface PlayerAdapter {
  /**
   * sets the media volume.
   *
   * @param volume: 0 is min, 1 is max
   */
  fun setVolume(volume: Float)

  /**
   * starts playing the sound
   */
  fun play()

  /**
   * stops playing the sound but does not release the underlying sound resource.
   */
  fun pause()

  /**
   * stops playing the sound and releases the underlying sound resource.
   */
  fun stop()
}

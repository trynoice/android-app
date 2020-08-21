package com.github.ashutoshgngwr.noice.sound.player.strategy

import com.github.ashutoshgngwr.noice.sound.Sound

/**
 * [PlaybackStrategyFactory] declares an abstract factory that can be implemented to create new
 * [PlaybackStrategy] instances.
 */
interface PlaybackStrategyFactory {

  /**
   * Returns a instance of a concrete implementation of [PlaybackStrategy].
   */
  fun newInstance(sound: Sound): PlaybackStrategy
}

package com.github.ashutoshgngwr.noice.sound.player.adapter

import com.github.ashutoshgngwr.noice.sound.Sound

/**
 * [PlayerAdapterFactory] declares an abstract factory that can be implemented to create new
 * [PlayerAdapter] instances.
 */
interface PlayerAdapterFactory {

  /**
   * Returns a concrete implementation of [PlayerAdapter].
   */
  fun newPlayerAdapter(sound: Sound): PlayerAdapter
}

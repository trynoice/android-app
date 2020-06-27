package com.github.ashutoshgngwr.noice.sound.player.strategy

import android.content.Context
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.sound.Sound

/**
 * [LocalPlaybackStrategyFactory] implements [PlaybackStrategyFactory] for creating [LocalPlaybackStrategy]
 * instances.
 */
class LocalPlaybackStrategyFactory(
  private val context: Context,
  private val audioAttributes: AudioAttributesCompat
) : PlaybackStrategyFactory {

  override fun newInstance(sound: Sound): PlaybackStrategy {
    return LocalPlaybackStrategy(context, audioAttributes, sound)
  }
}

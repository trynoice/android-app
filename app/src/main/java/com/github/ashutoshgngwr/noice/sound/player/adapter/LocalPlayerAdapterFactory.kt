package com.github.ashutoshgngwr.noice.sound.player.adapter

import android.content.Context
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.sound.Sound

/**
 * [LocalPlayerAdapterFactory] implements [PlayerAdapterFactory] for creating [LocalPlayerAdapter]
 * instances.
 */
class LocalPlayerAdapterFactory(
  private val context: Context,
  private val audioAttributes: AudioAttributesCompat
) : PlayerAdapterFactory {

  override fun newPlayerAdapter(sound: Sound): PlayerAdapter {
    return LocalPlayerAdapter(context, audioAttributes, sound)
  }
}

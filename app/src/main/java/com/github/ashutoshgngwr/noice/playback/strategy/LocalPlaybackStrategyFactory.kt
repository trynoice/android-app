package com.github.ashutoshgngwr.noice.playback.strategy

import android.content.Context
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * [LocalPlaybackStrategyFactory] implements [PlaybackStrategyFactory] for creating [LocalPlaybackStrategy]
 * instances.
 */
class LocalPlaybackStrategyFactory(
  private val context: Context,
  private val audioAttributes: AudioAttributesCompat
) : PlaybackStrategyFactory {

  private val entryPoint by lazy {
    EntryPointAccessors.fromApplication(
      context.applicationContext,
      LocalPlaybackStrategyEntryPoint::class.java
    )
  }

  override fun newInstance(sound: Sound): PlaybackStrategy {
    return LocalPlaybackStrategy(context, audioAttributes, sound, entryPoint.settingsRepository())
  }

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface LocalPlaybackStrategyEntryPoint {
    fun settingsRepository(): SettingsRepository
  }
}

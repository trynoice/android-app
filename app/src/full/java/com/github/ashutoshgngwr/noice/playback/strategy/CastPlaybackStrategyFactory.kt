package com.github.ashutoshgngwr.noice.playback.strategy

import android.content.Context
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.google.android.gms.cast.framework.CastSession
import com.google.gson.Gson
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent


/**
 * A [PlaybackStrategyFactory] for creating [PlaybackStrategy] instances that cast media using the Google
 * Cast application framework.
 */
class CastPlaybackStrategyFactory internal constructor(
  private val context: Context,
  private val session: CastSession,
  private val namespace: String
) : PlaybackStrategyFactory {

  private val entryPoint by lazy {
    EntryPointAccessors.fromApplication(
      context.applicationContext,
      CastPlaybackStrategyEntryPoint::class.java,
    )
  }

  override fun newInstance(sound: Sound): PlaybackStrategy {
    return CastPlaybackStrategy(
      session,
      namespace,
      sound,
      entryPoint.gson(),
      entryPoint.settingsRepository(),
    )
  }

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface CastPlaybackStrategyEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun gson(): Gson
  }
}

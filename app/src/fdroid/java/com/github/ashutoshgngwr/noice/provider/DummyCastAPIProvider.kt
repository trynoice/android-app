package com.github.ashutoshgngwr.noice.provider

import android.content.Context
import android.view.Menu
import androidx.media.VolumeProviderCompat
import com.github.ashutoshgngwr.noice.playback.strategy.PlaybackStrategyFactory

/**
 * [DummyCastAPIProvider] is a dummy concrete [CastAPIProvider] implementation used by the F-Droid
 * variant, to compile without actual non-free Google Cast API dependency.
 */
class DummyCastAPIProvider private constructor() : CastAPIProvider {

  companion object {
    val FACTORY = object : CastAPIProvider.Factory {
      override fun newInstance(context: Context) = DummyCastAPIProvider()
    }
  }

  override fun addMenuItem(menu: Menu, titleResId: Int) = Unit

  override fun getPlaybackStrategyFactory(): PlaybackStrategyFactory {
    throw IllegalStateException("F-Droid build shouldn't attempt to get CastPlaybackStrategyFactory")
  }

  override fun getVolumeProvider(): VolumeProviderCompat {
    throw IllegalStateException("F-Droid build shouldn't attempt to get CastVolumeProvider")
  }

  override fun onSessionBegin(callback: () -> Unit) = Unit

  override fun onSessionEnd(callback: () -> Unit) = Unit

  override fun clearSessionCallbacks() = Unit
}

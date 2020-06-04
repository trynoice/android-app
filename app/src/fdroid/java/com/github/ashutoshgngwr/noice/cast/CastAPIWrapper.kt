package com.github.ashutoshgngwr.noice.cast

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.media.VolumeProviderCompat
import com.github.ashutoshgngwr.noice.sound.player.adapter.PlayerAdapterFactory

/**
 * [CastAPIWrapper] wraps all API calls to GMS API and hides API specific classes from the caller.
 * The actual implementation is present in the 'playstore' source set while this dummy
 * implementation allows F-Droid flavor to be built without requiring GMS Cast framework
 * as a dependency.
 */
@Suppress("UNUSED_PARAMETER")
class CastAPIWrapper(context: Context, registerSessionCallbacks: Boolean) {

  fun setUpMenuItem(menu: Menu, @StringRes titleResId: Int): MenuItem? = null

  fun newCastPlayerAdapterFactory(): PlayerAdapterFactory {
    throw IllegalStateException("F-Droid build shouldn't attempt to create CastSoundPlayerFactory")
  }

  fun newCastVolumeProvider(): VolumeProviderCompat {
    throw IllegalStateException("F-Droid build shouldn't attempt to create CastVolumeProvider")
  }

  fun onSessionBegin(callback: () -> Unit) = Unit

  fun onSessionEnd(callback: () -> Unit) = Unit

  fun clearSessionCallbacks() = Unit
}

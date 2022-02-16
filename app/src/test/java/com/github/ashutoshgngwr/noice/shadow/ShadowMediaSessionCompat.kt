@file:Suppress("unused", "TestFunctionName")

package com.github.ashutoshgngwr.noice.shadow

import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.VolumeProviderCompat
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow.invokeConstructor
import org.robolectric.util.ReflectionHelpers.ClassParameter
import org.robolectric.util.reflector.ForType
import org.robolectric.util.reflector.Reflector.reflector

/**
 * Shadow implementation for [MediaSessionCompat].
 */
@Implements(value = MediaSessionCompat::class)
class ShadowMediaSessionCompat {

  companion object {
    var currentVolumeProvider: VolumeProviderCompat? = null
      private set

    private var currentPlaybackState: PlaybackStateCompat? = null

    fun getLastPlaybackState(): Int {
      return currentPlaybackState?.state ?: PlaybackStateCompat.STATE_NONE
    }
  }

  @RealObject
  lateinit var realMediaSessionCompat: MediaSessionCompat

  @Implementation
  fun __constructor__(context: Context, tag: String) {
    invokeConstructor(
      MediaSessionCompat::class.java,
      realMediaSessionCompat,
      ClassParameter.from(Context::class.java, context),
      ClassParameter.from(String::class.java, tag)
    )
  }

  @Implementation
  fun setPlaybackState(state: PlaybackStateCompat) {
    currentPlaybackState = state
    reflector(MediaSessionCompatReflector::class.java, realMediaSessionCompat)
      .setPlaybackState(state)
  }

  @Implementation
  fun setPlaybackToRemote(volumeProvider: VolumeProviderCompat) {
    currentVolumeProvider = volumeProvider
    reflector(MediaSessionCompatReflector::class.java, realMediaSessionCompat)
      .setPlaybackToRemote(volumeProvider)
  }

  @ForType(value = MediaSessionCompat::class, direct = true)
  private interface MediaSessionCompatReflector {
    fun setPlaybackState(state: PlaybackStateCompat)
    fun setPlaybackToRemote(volumeProvider: VolumeProviderCompat)
  }
}

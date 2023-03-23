@file:Suppress("unused", "TestFunctionName")

package com.github.ashutoshgngwr.noice.shadow

import android.content.Context
import android.support.v4.media.MediaMetadataCompat
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
    var currentMetadata: MediaMetadataCompat? = null; private set
    var currentPlaybackState: PlaybackStateCompat? = null; private set
    var isLocalPlayback = true; private set
    var currentAudioStream: Int? = null; private set
    var currentVolumeProvider: VolumeProviderCompat? = null; private set
    var currentCallback: MediaSessionCompat.Callback? = null; private set
    var isReleased = false; private set
  }

  @RealObject
  lateinit var realMediaSessionCompat: MediaSessionCompat

  @Implementation
  fun __constructor__(context: Context, tag: String) {
    currentMetadata = null
    currentPlaybackState = null
    isLocalPlayback = true
    currentAudioStream = null
    currentVolumeProvider = null
    currentCallback = null
    isReleased = false

    invokeConstructor(
      MediaSessionCompat::class.java,
      realMediaSessionCompat,
      ClassParameter.from(Context::class.java, context),
      ClassParameter.from(String::class.java, tag)
    )
  }

  @Implementation
  fun setMetadata(metadata: MediaMetadataCompat) {
    currentMetadata = metadata
    reflector(MediaSessionCompatReflector::class.java, realMediaSessionCompat)
      .setMetadata(metadata)
  }

  @Implementation
  fun setPlaybackState(state: PlaybackStateCompat) {
    currentPlaybackState = state
    reflector(MediaSessionCompatReflector::class.java, realMediaSessionCompat)
      .setPlaybackState(state)
  }

  @Implementation
  fun setPlaybackToLocal(stream: Int) {
    isLocalPlayback = true
    currentAudioStream = stream
    currentVolumeProvider = null
    reflector(MediaSessionCompatReflector::class.java, realMediaSessionCompat)
      .setPlaybackToLocal(stream)
  }

  @Implementation
  fun setPlaybackToRemote(volumeProvider: VolumeProviderCompat) {
    isLocalPlayback = false
    currentAudioStream = null
    currentVolumeProvider = volumeProvider
    reflector(MediaSessionCompatReflector::class.java, realMediaSessionCompat)
      .setPlaybackToRemote(volumeProvider)
  }

  @Implementation
  fun setCallback(callback: MediaSessionCompat.Callback) {
    currentCallback = callback
    reflector(MediaSessionCompatReflector::class.java, realMediaSessionCompat)
      .setCallback(callback)
  }

  @Implementation
  fun release() {
    isReleased = true
    reflector(MediaSessionCompatReflector::class.java, realMediaSessionCompat)
      .release()
  }

  @ForType(value = MediaSessionCompat::class, direct = true)
  private interface MediaSessionCompatReflector {
    fun setMetadata(metadata: MediaMetadataCompat)
    fun setPlaybackState(state: PlaybackStateCompat)
    fun setPlaybackToLocal(stream: Int)
    fun setPlaybackToRemote(volumeProvider: VolumeProviderCompat)
    fun setCallback(callback: MediaSessionCompat.Callback)
    fun release()
  }
}

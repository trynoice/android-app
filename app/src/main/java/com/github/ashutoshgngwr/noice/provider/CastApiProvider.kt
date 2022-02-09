package com.github.ashutoshgngwr.noice.provider

import android.content.Context
import android.view.Menu
import androidx.annotation.StringRes
import androidx.media.VolumeProviderCompat
import com.github.ashutoshgngwr.noice.playback.strategy.PlaybackStrategyFactory

/**
 * [CastApiProvider] is an abstract declaration of the non-free Google Cast APIs that are used in the app.
 * It effectively hides upstream classes from its callers. This is ensure that free variant
 * remains free of non-free Google Cast API dependency.
 */
interface CastApiProvider {

  /**
   * Adds a menu item to switch between local and cast playback to the given [menu].
   */
  fun addMenuItem(context: Context, menu: Menu, @StringRes titleResId: Int)

  /**
   * Returns a [PlaybackStrategyFactory] that plays media on a cast device.
   */
  fun getPlaybackStrategyFactory(context: Context): PlaybackStrategyFactory

  /**
   * Returns a [VolumeProviderCompat] that controls volume of the cast device.
   */
  fun getVolumeProvider(): VolumeProviderCompat

  /**
   * Registers a new [SessionListener]. It is a no-op if [SessionListener] was already registered.
   */
  fun registerSessionListener(listener: SessionListener)

  /**
   * Unregisters a registered [SessionListener]. It is a no-op if [SessionListener] wasn't
   * registered.
   */
  fun unregisterSessionListener(listener: SessionListener)

  /**
   * Declares a listener interface to listen for cast session callbacks.
   */
  interface SessionListener {
    /**
     * Invoked when cast session begins.
     */
    fun onSessionBegin()

    /**
     * Invoked when cast session ends.
     */
    fun onSessionEnd()
  }
}

/**
 * A no-op cast api provider for clients that don't have Google Mobile Services installed.
 */
object DummyCastApiProvider : CastApiProvider {

  override fun getPlaybackStrategyFactory(context: Context): PlaybackStrategyFactory {
    throw IllegalStateException("getPlaybackStrategyFactory() must not be invoked on DummyCastApiProvider")
  }

  override fun getVolumeProvider(): VolumeProviderCompat {
    throw IllegalStateException("getVolumeProvider() must not be invoked on DummyCastApiProvider")
  }

  override fun addMenuItem(context: Context, menu: Menu, titleResId: Int) = Unit
  override fun registerSessionListener(listener: CastApiProvider.SessionListener) = Unit
  override fun unregisterSessionListener(listener: CastApiProvider.SessionListener) = Unit
}

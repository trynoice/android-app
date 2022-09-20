package com.github.ashutoshgngwr.noice.provider

import android.content.Context
import android.view.Menu
import androidx.annotation.StringRes
import androidx.media.VolumeProviderCompat
import com.github.ashutoshgngwr.noice.engine.Player

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
   * Returns a [Player.Factory] that plays media on a cast device.
   */
  fun buildPlayerFactory(context: Context): Player.Factory

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
    fun onCastSessionBegin()

    /**
     * Invoked when cast session ends.
     */
    fun onCastSessionEnd()
  }
}

/**
 * A no-op cast api provider for clients that don't have Google Mobile Services installed.
 */
object DummyCastApiProvider : CastApiProvider {

  override fun buildPlayerFactory(context: Context): Player.Factory {
    throw IllegalStateException("getPlayerFactory() must not be invoked on DummyCastApiProvider")
  }

  override fun getVolumeProvider(): VolumeProviderCompat {
    throw IllegalStateException("getVolumeProvider() must not be invoked on DummyCastApiProvider")
  }

  override fun addMenuItem(context: Context, menu: Menu, titleResId: Int) = Unit
  override fun registerSessionListener(listener: CastApiProvider.SessionListener) = Unit
  override fun unregisterSessionListener(listener: CastApiProvider.SessionListener) = Unit
}

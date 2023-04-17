package com.github.ashutoshgngwr.noice.provider

import android.content.Context
import android.view.Menu
import androidx.annotation.StringRes
import com.github.ashutoshgngwr.noice.cast.CastReceiverUiManager
import com.github.ashutoshgngwr.noice.engine.SoundPlayer
import com.github.ashutoshgngwr.noice.engine.SoundPlayerManagerMediaSession

/**
 * [CastApiProvider] is an abstract declaration of the non-free Google Cast APIs that are used in
 * the app. It effectively hides upstream classes from its callers. This is ensure that the free
 * variant remains free of non-free Google Cast API dependency.
 */
interface CastApiProvider {

  /**
   * Adds a menu item to switch between local and cast playback to the given [menu].
   */
  fun addMenuItem(context: Context, menu: Menu, @StringRes titleResId: Int)

  /**
   * @return a [SoundPlayer.Factory] that builds [SoundPlayer] instances to play sounds on a cast
   * device.
   * @throws IllegalArgumentException if the Cast SDK fails to load on the device.
   */
  fun getSoundPlayerFactory(): SoundPlayer.Factory

  /**
   * @return a volume provider that controls volume of the cast device.
   * @throws IllegalArgumentException if the Cast SDK fails to load on the device.
   */
  fun getVolumeProvider(): SoundPlayerManagerMediaSession.RemoteDeviceVolumeProvider

  fun getReceiverUiManager(): CastReceiverUiManager

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
 * A no-op cast API provider for clients that don't have Google Mobile Services installed.
 */
object DummyCastApiProvider : CastApiProvider {

  override fun getSoundPlayerFactory(): SoundPlayer.Factory {
    throw IllegalStateException("getSoundPlayerFactory() must not be invoked on DummyCastApiProvider")
  }

  override fun getVolumeProvider(): SoundPlayerManagerMediaSession.RemoteDeviceVolumeProvider {
    throw IllegalStateException("getVolumeProvider() must not be invoked on DummyCastApiProvider")
  }

  override fun getReceiverUiManager(): CastReceiverUiManager {
    throw IllegalStateException("getUiManager() must not be invoked on DummyCastApiProvider")
  }

  override fun addMenuItem(context: Context, menu: Menu, titleResId: Int) = Unit
  override fun registerSessionListener(listener: CastApiProvider.SessionListener) = Unit
  override fun unregisterSessionListener(listener: CastApiProvider.SessionListener) = Unit
}

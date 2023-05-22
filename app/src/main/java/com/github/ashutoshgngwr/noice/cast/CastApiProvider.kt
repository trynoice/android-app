package com.github.ashutoshgngwr.noice.cast

import android.content.Context
import android.view.Menu
import androidx.annotation.StringRes
import com.github.ashutoshgngwr.noice.engine.SoundPlaybackMediaSession
import com.github.ashutoshgngwr.noice.engine.SoundPlayer

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
  fun getVolumeProvider(): SoundPlaybackMediaSession.RemoteDeviceVolumeProvider

  fun getReceiverUiManager(): CastReceiverUiManager

  /**
   * Registers a new [SessionListener]. It is a no-op if [SessionListener] was already registered.
   */
  fun addSessionListener(listener: SessionListener)

  /**
   * Unregisters a registered [SessionListener]. It is a no-op if [SessionListener] wasn't
   * registered.
   */
  fun removeSessionListener(listener: SessionListener)

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

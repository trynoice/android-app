package com.github.ashutoshgngwr.noice.provider

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.core.view.MenuItemCompat
import androidx.media.VolumeProviderCompat
import androidx.mediarouter.app.MediaRouteActionProvider
import com.github.ashutoshgngwr.noice.cast.CastPlayer
import com.github.ashutoshgngwr.noice.cast.CastVolumeProvider
import com.github.ashutoshgngwr.noice.engine.Player
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener

/**
 * [RealCastApiProvider] wraps all the Google Cast API functionality used by the application
 * for the full build variant.
 */
class RealCastApiProvider(context: Context) : CastApiProvider, SessionManagerListener<CastSession> {

  private val sessionListeners = mutableSetOf<CastApiProvider.SessionListener>()
  private val castContext = CastContext.getSharedInstance(context)

  /**
   * Sets up the cast media menu item on the given menu with given title resource.
   */
  override fun addMenuItem(context: Context, menu: Menu, @StringRes titleResId: Int) {
    menu.add(titleResId).also {
      it.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
      MenuItemCompat.setActionProvider(it, MediaRouteActionProvider(context))
      CastButtonFactory.setUpMediaRouteButton(context, menu, it.itemId)
    }
  }

  /**
   * Initializes a new [CastPlayer.Factory] instance and returns it as [Player.Factory].
   */
  override fun getPlayerFactory(context: Context): Player.Factory =
    CastPlayer.Factory(castContext, "urn:x-cast:com.github.ashutoshgngwr.noice:Player")

  /**
   * Creates a new [VolumeProviderCompat] instance that can be used with
   * [android.support.v4.media.session.MediaSessionCompat.setPlaybackToRemote].
   */
  override fun getVolumeProvider(): VolumeProviderCompat = CastVolumeProvider(castContext)

  override fun registerSessionListener(listener: CastApiProvider.SessionListener) {
    // add `castSessionManagerListener` only when the first `CastApiProvider.SessionListener` is
    // registered.
    if (sessionListeners.isEmpty()) {
      castContext.sessionManager.addSessionManagerListener(this, CastSession::class.java)
    }

    sessionListeners.add(listener)
  }

  override fun unregisterSessionListener(listener: CastApiProvider.SessionListener) {
    sessionListeners.remove(listener)

    // remove `castSessionManagerListener` when the last `CastApiProvider.SessionListener` is
    // unregistered.
    if (sessionListeners.isEmpty()) {
      castContext.sessionManager.removeSessionManagerListener(this, CastSession::class.java)
    }
  }

  override fun onSessionStarted(session: CastSession, sessionId: String) {
    sessionListeners.forEach { it.onCastSessionBegin() }
  }

  override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
    sessionListeners.forEach { it.onCastSessionBegin() }
  }

  override fun onSessionEnded(session: CastSession, error: Int) {
    sessionListeners.forEach { it.onCastSessionEnd() }
  }

  override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
  override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
  override fun onSessionStarting(session: CastSession) = Unit
  override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
  override fun onSessionEnding(session: CastSession) = Unit
  override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
}

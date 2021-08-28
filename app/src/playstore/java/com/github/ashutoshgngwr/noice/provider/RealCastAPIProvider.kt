package com.github.ashutoshgngwr.noice.provider

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.core.view.MenuItemCompat
import androidx.media.VolumeProviderCompat
import androidx.mediarouter.app.MediaRouteActionProvider
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.playback.strategy.CastPlaybackStrategyFactory
import com.github.ashutoshgngwr.noice.playback.strategy.PlaybackStrategyFactory
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener

/**
 * [RealCastAPIProvider] wraps all the Google Cast API functionality used by the application
 * for the Play Store build variant.
 */
class RealCastAPIProvider private constructor(val context: Context) : CastAPIProvider {

  companion object {
    val FACTORY = object : CastAPIProvider.Factory {
      override fun newInstance(context: Context) = RealCastAPIProvider(context)
    }
  }

  private val castContext = CastContext.getSharedInstance(context)
  private var sessionBeginCallback = { }
  private var sessionEndCallback = { }

  private val castSessionManagerListener = object : CastSessionManagerListener() {

    override fun onSessionStarted(session: CastSession, sessionId: String) = sessionBeginCallback()
    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) =
      sessionBeginCallback()

    override fun onSessionEnded(session: CastSession, error: Int) = sessionEndCallback()
  }

  init {
    castContext.sessionManager.addSessionManagerListener(
      castSessionManagerListener,
      CastSession::class.java
    )
  }

  /**
   * Sets up the cast media menu item on the given menu with given title resource.
   */
  override fun addMenuItem(menu: Menu, @StringRes titleResId: Int) {
    menu.add(titleResId).also {
      it.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
      MenuItemCompat.setActionProvider(it, MediaRouteActionProvider(context))
      CastButtonFactory.setUpMediaRouteButton(context, menu, it.itemId)
    }
  }

  /**
   * Initializes a new [CastPlaybackStrategyFactory] instance and returns it as
   * [PlaybackStrategyFactory].
   */
  override fun getPlaybackStrategyFactory(): PlaybackStrategyFactory =
    CastPlaybackStrategyFactory(
      context,
      requireNotNull(castContext.sessionManager.currentCastSession),
      context.getString(R.string.cast_namespace__default)
    )

  /**
   * Creates a new [VolumeProviderCompat] instance that can be used with
   * [android.support.v4.media.session.MediaSessionCompat.setPlaybackToRemote].
   */
  override fun getVolumeProvider(): VolumeProviderCompat =
    CastVolumeProvider(requireNotNull(castContext.sessionManager.currentCastSession))

  /**
   * Sets a convenience lambda that is called in session started and resumed callbacks.
   */
  override fun onSessionBegin(callback: () -> Unit) {
    this.sessionBeginCallback = callback
  }

  /**
   * Sets a convenience lambda that is called in session ended callbacks
   */
  override fun onSessionEnd(callback: () -> Unit) {
    this.sessionEndCallback = callback
  }

  /**
   * Removes the [CastSessionManagerListener] that is registered in when [RealCastAPIProvider] instance
   * is created.
   */
  override fun clearSessionCallbacks() {
    castContext.sessionManager.removeSessionManagerListener(
      castSessionManagerListener,
      CastSession::class.java
    )
  }

  /**
   * A helper class for allowing to implement the required methods wherever needed.
   */
  open class CastSessionManagerListener : SessionManagerListener<CastSession> {
    override fun onSessionStarted(session: CastSession, sessionId: String) = Unit
    override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
    override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    override fun onSessionEnded(session: CastSession, error: Int) = Unit
    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = Unit
    override fun onSessionStarting(session: CastSession) = Unit
    override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
    override fun onSessionEnding(session: CastSession) = Unit
    override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
  }
}

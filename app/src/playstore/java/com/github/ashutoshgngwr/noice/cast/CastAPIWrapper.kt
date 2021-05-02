package com.github.ashutoshgngwr.noice.cast

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.core.view.MenuItemCompat
import androidx.media.VolumeProviderCompat
import androidx.mediarouter.app.MediaRouteActionProvider
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.cast.playback.strategy.CastPlaybackStrategyFactory
import com.github.ashutoshgngwr.noice.playback.strategy.PlaybackStrategyFactory
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession

/**
 * [CastAPIWrapper] wraps all API calls to GMS API and hides API specific classes from the caller.
 * This is required to ensure that F-Droid flavor can be built without requiring GMS Cast framework
 * as a dependency.
 */
class CastAPIWrapper private constructor(val context: Context, registerSessionCallbacks: Boolean) {

  companion object {
    // there is no way to mock constructor in tests so mocking Companion object instead
    fun from(context: Context, registerSessionCallbacks: Boolean): CastAPIWrapper {
      return CastAPIWrapper(context, registerSessionCallbacks)
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
    if (registerSessionCallbacks) {
      castContext.sessionManager.addSessionManagerListener(
        castSessionManagerListener,
        CastSession::class.java
      )
    }
  }

  /**
   * Sets up the cast media menu item on the given menu with given title resource.
   */
  fun setUpMenuItem(menu: Menu, @StringRes titleResId: Int): MenuItem? =
    menu.add(titleResId).also {
      it.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
      MenuItemCompat.setActionProvider(it, MediaRouteActionProvider(context))
      CastButtonFactory.setUpMediaRouteButton(context, menu, it.itemId)
    }

  /**
   * Initializes a new [CastPlaybackStrategyFactory] instance and returns it as
   * [PlaybackStrategyFactory].
   */
  fun newCastPlaybackStrategyFactory(): PlaybackStrategyFactory =
    CastPlaybackStrategyFactory(
      requireNotNull(castContext.sessionManager.currentCastSession),
      context.getString(R.string.cast_namespace__default)
    )

  /**
   * Creates a new [VolumeProviderCompat] instance that can be used with
   * [android.support.v4.media.session.MediaSessionCompat.setPlaybackToRemote].
   */
  fun newCastVolumeProvider(): VolumeProviderCompat =
    CastVolumeProvider(requireNotNull(castContext.sessionManager.currentCastSession))

  /**
   * Sets a convenience lambda that is called in session started and resumed callbacks.
   */
  fun onSessionBegin(callback: () -> Unit) {
    this.sessionBeginCallback = callback
  }

  /**
   * Sets a convenience lambda that is called in session ended callbacks
   */
  fun onSessionEnd(callback: () -> Unit) {
    this.sessionEndCallback = callback
  }

  /**
   * Removes the [CastSessionManagerListener] that is registered in when [CastAPIWrapper] instance
   * is created.
   */
  fun clearSessionCallbacks() {
    castContext.sessionManager.removeSessionManagerListener(
      castSessionManagerListener,
      CastSession::class.java
    )
  }
}

package com.github.ashutoshgngwr.noice.provider

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.core.view.MenuItemCompat
import androidx.media.VolumeProviderCompat
import androidx.mediarouter.app.MediaRouteActionProvider
import com.github.ashutoshgngwr.noice.cast.CastMessagingChannel
import com.github.ashutoshgngwr.noice.cast.CastSoundPlayer
import com.github.ashutoshgngwr.noice.cast.CastUiManager
import com.github.ashutoshgngwr.noice.cast.CastVolumeProvider
import com.github.ashutoshgngwr.noice.cast.DefaultCastUiManager
import com.github.ashutoshgngwr.noice.cast.models.Event
import com.github.ashutoshgngwr.noice.cast.models.GetAccessTokenEvent
import com.github.ashutoshgngwr.noice.cast.models.GetAccessTokenResponseEvent
import com.github.ashutoshgngwr.noice.engine.SoundPlayer
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.gson.Gson
import java.util.concurrent.Executors

/**
 * [RealCastApiProvider] wraps all the Google Cast API functionality used by the application
 * for the full build variant.
 */
class RealCastApiProvider(
  context: Context,
  private val accessTokenGetter: AccessTokenGetter,
  private val gson: Gson,
) : CastApiProvider, CastMessagingChannel.EventListener, SessionManagerListener<CastSession> {

  private val handler = Handler(Looper.getMainLooper())
  private val sessionListeners = mutableSetOf<CastApiProvider.SessionListener>()
  private var castContext: CastContext? = null
  private var authMessagingChannel: CastMessagingChannel? = null

  init {
    CastContext.getSharedInstance(context, Executors.newSingleThreadExecutor())
      .addOnSuccessListener { castContext = it }
      .addOnFailureListener { Log.w(LOG_TAG, "init: failed to get cast context instance", it) }
  }

  override fun addMenuItem(context: Context, menu: Menu, @StringRes titleResId: Int) {
    menu.add(titleResId).also {
      it.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
      MenuItemCompat.setActionProvider(it, MediaRouteActionProvider(context))
      CastButtonFactory.setUpMediaRouteButton(context, menu, it.itemId)
    }
  }

  override fun getSoundPlayerFactory(): SoundPlayer.Factory {
    val messagingChannel = CastMessagingChannel(
      castContext = requireNotNull(castContext),
      namespace = "urn:x-cast:com.github.ashutoshgngwr.noice:sounds",
      gson = gson,
      handler = handler,
    )

    return CastSoundPlayer.Factory(messagingChannel)
  }

  override fun getVolumeProvider(): VolumeProviderCompat =
    CastVolumeProvider(requireNotNull(castContext))

  override fun getUiManager(): CastUiManager {
    val messagingChannel = CastMessagingChannel(
      castContext = requireNotNull(castContext),
      namespace = "urn:x-cast:com.github.ashutoshgngwr.noice:ui-updates",
      gson = gson,
      handler = handler,
    )

    return DefaultCastUiManager(messagingChannel)
  }

  override fun registerSessionListener(listener: CastApiProvider.SessionListener) {
    // add `castSessionManagerListener` only when the first `CastApiProvider.SessionListener` is
    // registered.
    if (sessionListeners.isEmpty()) {
      castContext?.sessionManager?.addSessionManagerListener(this, CastSession::class.java)
    }

    sessionListeners.add(listener)
  }

  override fun unregisterSessionListener(listener: CastApiProvider.SessionListener) {
    sessionListeners.remove(listener)

    // remove `castSessionManagerListener` when the last `CastApiProvider.SessionListener` is
    // unregistered.
    if (sessionListeners.isEmpty()) {
      castContext?.sessionManager?.removeSessionManagerListener(this, CastSession::class.java)
    }
  }

  override fun onEventReceived(event: Event) {
    if (event !is GetAccessTokenEvent) {
      return
    }

    Log.d(LOG_TAG, "onEventReceived: sending access token to the receiver app")
    accessTokenGetter.get { authMessagingChannel?.send(GetAccessTokenResponseEvent(it)) }
  }

  override fun onSessionStarted(session: CastSession, sessionId: String) {
    onSessionStarted()
  }

  override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
    onSessionStarted()
  }

  private fun onSessionStarted() {
    authMessagingChannel = CastMessagingChannel(
      castContext = requireNotNull(castContext),
      namespace = "urn:x-cast:com.github.ashutoshgngwr.noice:auth",
      gson = gson,
      handler = handler,
    )

    authMessagingChannel?.addEventListener(this)
    sessionListeners.forEach { listener ->
      handler.post { listener.onCastSessionBegin() }
    }
  }

  override fun onSessionEnded(session: CastSession, error: Int) {
    authMessagingChannel?.removeEventListener(this)
    authMessagingChannel = null
    sessionListeners.forEach { listener ->
      handler.post { listener.onCastSessionEnd() }
    }
  }

  override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
  override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
  override fun onSessionStarting(session: CastSession) = Unit
  override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
  override fun onSessionEnding(session: CastSession) = Unit
  override fun onSessionStartFailed(session: CastSession, error: Int) = Unit

  companion object {
    private const val LOG_TAG = "RealCastApiProvider"
  }

  fun interface AccessTokenGetter {
    fun get(callback: (token: String?) -> Unit)
  }
}

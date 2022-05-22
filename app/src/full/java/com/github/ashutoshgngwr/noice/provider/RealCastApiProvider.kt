package com.github.ashutoshgngwr.noice.provider

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.view.MenuItemCompat
import androidx.media.VolumeProviderCompat
import androidx.mediarouter.app.MediaRouteActionProvider
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.R
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import kotlin.math.round

/**
 * [RealCastApiProvider] wraps all the Google Cast API functionality used by the application
 * for the full build variant.
 */
class RealCastApiProvider(context: Context) : CastApiProvider {

  private val sessionListeners = mutableSetOf<CastApiProvider.SessionListener>()

  private val castContext = CastContext.getSharedInstance(context)
  private val castSessionManagerListener = object : SessionManagerListener<CastSession> {
    override fun onSessionStarted(session: CastSession, sessionId: String) {
      sessionListeners.forEach { it.onSessionBegin() }
    }

    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
      sessionListeners.forEach { it.onSessionBegin() }
    }

    override fun onSessionEnded(session: CastSession, error: Int) {
      sessionListeners.forEach { it.onSessionEnd() }
    }

    override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
    override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    override fun onSessionStarting(session: CastSession) = Unit
    override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
    override fun onSessionEnding(session: CastSession) = Unit
    override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
  }

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
   * Initializes a new [CastPlaybackStrategyFactory] instance and returns it as
   * [PlaybackStrategyFactory].
   */
// TODO: implement this.
//  override fun getPlaybackStrategyFactory(context: Context): PlaybackStrategyFactory {
//  }

  /**
   * Creates a new [VolumeProviderCompat] instance that can be used with
   * [android.support.v4.media.session.MediaSessionCompat.setPlaybackToRemote].
   */
  override fun getVolumeProvider(): VolumeProviderCompat =
    CastVolumeProvider(requireNotNull(castContext.sessionManager.currentCastSession))

  override fun registerSessionListener(listener: CastApiProvider.SessionListener) {
    // add `castSessionManagerListener` only when the first `CastApiProvider.SessionListener` is
    // registered.
    if (sessionListeners.isEmpty()) {
      castContext.sessionManager.addSessionManagerListener(
        castSessionManagerListener,
        CastSession::class.java
      )
    }

    sessionListeners.add(listener)
  }

  override fun unregisterSessionListener(listener: CastApiProvider.SessionListener) {
    sessionListeners.remove(listener)

    // remove `castSessionManagerListener` when the last `CastApiProvider.SessionListener` is
    // unregistered.
    if (sessionListeners.isEmpty()) {
      castContext.sessionManager.removeSessionManagerListener(
        castSessionManagerListener,
        CastSession::class.java
      )
    }
  }
}

@Suppress("unused") // Referred from 'AndroidManifest.xml'
class CastOptionsProvider : OptionsProvider {

  override fun getCastOptions(context: Context): CastOptions {
    return CastOptions.Builder().run {
      setReceiverApplicationId(
        context.getString(
          @Suppress("ConstantConditionIf")
          if (BuildConfig.DEBUG) {
            R.string.cast_app_id__debug
          } else {
            R.string.cast_app_id__release
          }
        )
      )

      setStopReceiverApplicationWhenEndingSession(true)
      setCastMediaOptions(
        CastMediaOptions.Builder()
          .setMediaSessionEnabled(false)
          .build()
      )

      build()
    }
  }

  override fun getAdditionalSessionProviders(context: Context): MutableList<SessionProvider>? = null
}

/**
 * A [VolumeProviderCompat] implementation for adjusting cast device volume using active
 * [MediaSession][android.support.v4.media.session.MediaSessionCompat]'s remote playback
 * control.
 */
internal class CastVolumeProvider(private val session: CastSession) :
  VolumeProviderCompat(VOLUME_CONTROL_ABSOLUTE, MAX_VOLUME, multiply(session.volume)) {

  companion object {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val MAX_VOLUME = 15

    private fun multiply(volume: Double): Int {
      return round(volume * MAX_VOLUME).toInt()
    }
  }

  override fun onSetVolumeTo(volume: Int) {
    session.volume = volume.toDouble() / MAX_VOLUME
    this.currentVolume = volume
  }

  override fun onAdjustVolume(direction: Int) {
    onSetVolumeTo(this.currentVolume + direction)
  }
}

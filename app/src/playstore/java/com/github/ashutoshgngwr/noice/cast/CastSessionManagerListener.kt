package com.github.ashutoshgngwr.noice.cast

import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener

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

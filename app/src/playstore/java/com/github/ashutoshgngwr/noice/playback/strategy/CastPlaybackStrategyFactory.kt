package com.github.ashutoshgngwr.noice.playback.strategy

import android.content.Context
import com.github.ashutoshgngwr.noice.model.Sound
import com.google.android.gms.cast.framework.CastSession


/**
 * A [PlaybackStrategyFactory] for creating [PlaybackStrategy] instances that cast media using the Google
 * Cast application framework.
 */
class CastPlaybackStrategyFactory internal constructor(
  private val context: Context,
  private val session: CastSession,
  private val namespace: String
) : PlaybackStrategyFactory {

  override fun newInstance(sound: Sound): PlaybackStrategy {
    return CastPlaybackStrategy(context, session, namespace, sound)
  }
}

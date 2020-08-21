package com.github.ashutoshgngwr.noice.cast.player.strategy

import com.github.ashutoshgngwr.noice.sound.Sound
import com.github.ashutoshgngwr.noice.sound.player.strategy.PlaybackStrategy
import com.github.ashutoshgngwr.noice.sound.player.strategy.PlaybackStrategyFactory
import com.google.android.gms.cast.framework.CastSession


/**
 * A [PlaybackStrategyFactory] for creating [PlaybackStrategy] instances that cast media using the Google
 * Cast application framework.
 */
class CastPlaybackStrategyFactory internal constructor(
  private val session: CastSession,
  private val namespace: String
) : PlaybackStrategyFactory {

  override fun newInstance(sound: Sound): PlaybackStrategy {
    return CastPlaybackStrategy(session, namespace, sound)
  }
}

package com.github.ashutoshgngwr.noice.cast.player.adapter

import com.github.ashutoshgngwr.noice.sound.Sound
import com.github.ashutoshgngwr.noice.sound.player.adapter.PlayerAdapter
import com.github.ashutoshgngwr.noice.sound.player.adapter.PlayerAdapterFactory
import com.google.android.gms.cast.framework.CastSession


/**
 * A [PlayerAdapterFactory] for creating [PlayerAdapter] instances that cast media using the Google
 * Cast application framework.
 */
class CastPlayerAdapterFactory internal constructor(
  private val session: CastSession,
  private val namespace: String
) : PlayerAdapterFactory {

  override fun newPlayerAdapter(sound: Sound): PlayerAdapter {
    return CastPlayerAdapter(session, namespace, sound)
  }
}

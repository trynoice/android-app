package com.github.ashutoshgngwr.noice.cast.player

import com.github.ashutoshgngwr.noice.sound.Sound
import com.github.ashutoshgngwr.noice.sound.player.SoundPlayer
import com.github.ashutoshgngwr.noice.sound.player.SoundPlayerFactory
import com.google.android.gms.cast.framework.CastSession


/**
 * A [SoundPlayerFactory] for creating [SoundPlayer] instances that cast media using the Google
 * Cast application framework.
 */
class CastSoundPlayerFactory internal constructor(
  private val session: CastSession,
  private val namespace: String
) : SoundPlayerFactory {
  override fun newPlayer(sound: Sound): SoundPlayer {
    return CastSoundPlayer(session, namespace, sound)
  }

}

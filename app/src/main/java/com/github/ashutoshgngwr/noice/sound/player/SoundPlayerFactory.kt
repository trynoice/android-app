package com.github.ashutoshgngwr.noice.sound.player

import android.content.Context
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.sound.Sound
import com.google.android.gms.cast.framework.CastSession

/**
 * [SoundPlayerFactory] is the factory interface to creating new sound player
 * instances.
 */
interface SoundPlayerFactory {

  /**
   * Returns a new [SoundPlayer] instance.
   */
  fun newPlayer(sound: Sound): SoundPlayer

  /**
   * A [SoundPlayerFactory] for creating [SoundPlayer] instances that play media locally using
   * [SimpleExoPlayer][com.google.android.exoplayer2.SimpleExoPlayer] instances.
   */
  class LocalSoundPlayerFactory internal constructor(
    private val context: Context,
    private val audioAttributesCompat: AudioAttributesCompat
  ) : SoundPlayerFactory {
    override fun newPlayer(sound: Sound): SoundPlayer {
      return LocalSoundPlayer(context, audioAttributesCompat, sound)
    }
  }

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
}

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

  private class LocalSoundPlayerFactory(
    private val context: Context,
    private val audioAttributesCompat: AudioAttributesCompat
  ) : SoundPlayerFactory {
    override fun newPlayer(sound: Sound): SoundPlayer {
      return LocalSoundPlayer(context, audioAttributesCompat, sound)
    }
  }

  private class CastSoundPlayerFactory(
    private val session: CastSession,
    private val namespace: String
  ) : SoundPlayerFactory {
    override fun newPlayer(sound: Sound): SoundPlayer {
      return CastSoundPlayer(session, namespace, sound)
    }

  }

  companion object {
    /**
     * Create a new [SoundPlayerFactory] for creating [LocalSoundPlayer] instances.
     */
    fun newLocalPlayerFactory(
      context: Context,
      audioAttributes: AudioAttributesCompat
    ): SoundPlayerFactory {
      return LocalSoundPlayerFactory(context, audioAttributes)
    }

    /**
     * Create a new [SoundPlayerFactory] for creating [CastSoundPlayer] instances.
     */
    fun newCastPlayerFactory(session: CastSession, namespace: String): SoundPlayerFactory {
      return CastSoundPlayerFactory(session, namespace)
    }
  }
}

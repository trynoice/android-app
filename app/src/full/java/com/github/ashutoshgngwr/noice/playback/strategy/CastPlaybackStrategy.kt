package com.github.ashutoshgngwr.noice.playback.strategy

import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.google.android.gms.cast.framework.CastSession
import com.google.gson.Gson
import com.google.gson.annotations.Expose

/**
 * [CastPlaybackStrategy] implements [PlaybackStrategy] that sends the control events
 * to the cast receiver application.
 */
class CastPlaybackStrategy(
  private val session: CastSession,
  private val namespace: String,
  private val sound: Sound,
  private val gson: Gson,
  private val settingsRepository: SettingsRepository,
) : PlaybackStrategy {

  companion object {
    private const val ACTION_CREATE = "create"
    private const val ACTION_PLAY = "play"
    private const val ACTION_PAUSE = "pause"
    private const val ACTION_STOP = "stop"
  }

  @Suppress("unused")
  private class PlayerEvent(
    @Expose val src: Array<String>,
    @Expose val isLooping: Boolean,
    @Expose val volume: Float,
    @Expose val action: String?,
    @Expose val fadeInDuration: Long,
  )

  var volume: Float = 0.0f
    private set

  init {
    notifyChanges(ACTION_CREATE)
  }

  override fun setVolume(volume: Float) {
    if (this.volume == volume) {
      return
    }

    this.volume = volume
    notifyChanges(null)
  }

  override fun play() {
    notifyChanges(ACTION_PLAY)
  }

  override fun pause() {
    notifyChanges(ACTION_PAUSE)
  }

  override fun stop() {
    notifyChanges(ACTION_STOP)
  }

  override fun setAudioAttributes(attrs: AudioAttributesCompat) = Unit

  private fun notifyChanges(action: String?) {
    // TODO:
//    val fadeInDuration = settingsRepository.getSoundFadeInDurationMillis()
//    val event = PlayerEvent(sound.src, sound.isLooping, volume, action, fadeInDuration)
//    session.sendMessage(namespace, gson.toJson(event))
  }
}

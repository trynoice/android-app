package com.github.ashutoshgngwr.noice.cast.player.adapter

import com.github.ashutoshgngwr.noice.sound.Sound
import com.github.ashutoshgngwr.noice.sound.player.adapter.PlayerAdapter
import com.google.android.gms.cast.framework.CastSession
import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose

/**
 * [CastPlayerAdapter] implements [PlayerAdapter] that sends the control events
 * to the cast receiver application.
 */
class CastPlayerAdapter(
  private val session: CastSession,
  private val namespace: String,
  private val sound: Sound
) : PlayerAdapter {

  companion object {
    private const val ACTION_CREATE = "create"
    private const val ACTION_PLAY = "play"
    private const val ACTION_PAUSE = "pause"
    private const val ACTION_STOP = "stop"
  }

  private data class PlayerEvent(
    @Expose val soundKey: String,
    @Expose val isLooping: Boolean,
    @Expose val volume: Float,
    @Expose val action: String?
  )

  var volume: Float = 0.0f
    private set

  private val gson = GsonBuilder()
    .excludeFieldsWithoutExposeAnnotation()
    .create()

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

  private fun notifyChanges(action: String?) {
    session.sendMessage(
      namespace, gson.toJson(PlayerEvent(sound.key, sound.isLoopable, volume, action))
    )
  }
}

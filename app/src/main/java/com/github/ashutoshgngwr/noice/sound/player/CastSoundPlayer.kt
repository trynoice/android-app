package com.github.ashutoshgngwr.noice.sound.player

import com.github.ashutoshgngwr.noice.sound.Sound
import com.google.android.gms.cast.framework.CastSession
import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose

/**
 * [CastSoundPlayer] implements [SoundPlayer] that sends the control events
 * to the cast receiver application.
 */
class CastSoundPlayer(
  private val session: CastSession,
  private val namespace: String,
  sound: Sound
) : SoundPlayer() {

  /**
   * [State] denotes all possible states that [CastSoundPlayer] can be in at any given instant.
   * This enum is private to CastSoundPlayer but its visibility is public because Gson needs to
   * access it.
   */
  enum class State {
    PLAYING, PAUSED, STOPPED
  }

  @Expose
  @Suppress("unused")
  val soundKey = sound.key

  @Expose
  @Suppress("unused")
  val isLooping = sound.isLoopable

  @Expose
  var volume: Float = 0.0f
    private set

  @Expose
  var state = State.STOPPED

  private val gson = GsonBuilder()
    .excludeFieldsWithoutExposeAnnotation()
    .create()

  override fun setVolume(volume: Float) {
    this.volume = volume

    // since volume update will only take effect during the PLAYING state, it would be
    // redundant to send updates for others. Once the player comes back to PLAYING state
    // the volume will be updated along with state update.
    if (state == State.PLAYING) {
      notifyChanges()
    }
  }

  override fun play() {
    this.state = State.PLAYING
    notifyChanges()
  }

  override fun pause() {
    this.state = State.PAUSED
    notifyChanges()
  }

  override fun stop() {
    this.state = State.STOPPED
    notifyChanges()
  }

  private fun notifyChanges() {
    session.sendMessage(namespace, gson.toJson(this))
  }
}

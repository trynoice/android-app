package com.github.ashutoshgngwr.noice.engine

import androidx.media3.common.AudioAttributes
import kotlin.time.Duration

class FakeSoundPlayer private constructor() : SoundPlayer() {

  var fadeInDuration = Duration.ZERO; private set
  var fadeOutDuration = Duration.ZERO; private set
  var isPremiumSegmentsEnabled = false; private set
  var audioBitrate: String = "128k"; private set
  var audioAttributes: AudioAttributes? = null; private set
  var volume = 1F; private set

  override fun setFadeInDuration(duration: Duration) {
    fadeInDuration = duration
  }

  override fun setFadeOutDuration(duration: Duration) {
    fadeOutDuration = duration
  }

  override fun setPremiumSegmentsEnabled(enabled: Boolean) {
    isPremiumSegmentsEnabled = enabled
  }

  override fun setAudioBitrate(bitrate: String) {
    audioBitrate = bitrate
  }

  override fun setAudioAttributes(attrs: AudioAttributes) {
    audioAttributes = attrs
  }

  override fun setVolume(volume: Float) {
    this.volume = volume
  }

  override fun play() {
    state = State.BUFFERING
  }

  override fun pause(immediate: Boolean) {
    state = if (immediate) State.PAUSED else State.PAUSING
  }

  override fun stop(immediate: Boolean) {
    state = if (immediate) State.STOPPED else State.STOPPING
  }

  fun setStateTo(state: State) {
    this.state = state
  }

  class Factory : SoundPlayer.Factory {

    private val _builtInstances = mutableMapOf<String, FakeSoundPlayer>()
    val builtInstances: Map<String, FakeSoundPlayer> = _builtInstances

    override fun buildPlayer(soundId: String): SoundPlayer {
      _builtInstances[soundId] = FakeSoundPlayer()
      return _builtInstances.getValue(soundId)
    }
  }
}

package com.github.ashutoshgngwr.noice.cast

import androidx.annotation.VisibleForTesting
import androidx.media3.common.AudioAttributes
import com.github.ashutoshgngwr.noice.cast.models.EnableSoundPremiumSegmentsEvent
import com.github.ashutoshgngwr.noice.cast.models.Event
import com.github.ashutoshgngwr.noice.cast.models.PauseSoundEvent
import com.github.ashutoshgngwr.noice.cast.models.PlaySoundEvent
import com.github.ashutoshgngwr.noice.cast.models.SetSoundAudioBitrateEvent
import com.github.ashutoshgngwr.noice.cast.models.SetSoundFadeInDurationEvent
import com.github.ashutoshgngwr.noice.cast.models.SetSoundFadeOutDurationEvent
import com.github.ashutoshgngwr.noice.cast.models.SetSoundVolumeEvent
import com.github.ashutoshgngwr.noice.cast.models.SoundStateChangedEvent
import com.github.ashutoshgngwr.noice.cast.models.StopSoundEvent
import com.github.ashutoshgngwr.noice.engine.SoundPlayer
import kotlin.time.Duration

class CastSoundPlayer @VisibleForTesting constructor(
  private val soundId: String,
  private val messagingChannel: CastMessagingChannel,
) : SoundPlayer(), CastMessagingChannel.EventListener {

  init {
    messagingChannel.addEventListener(this)
  }

  override fun setFadeInDuration(duration: Duration) {
    messagingChannel.send(SetSoundFadeInDurationEvent(soundId, duration.inWholeMilliseconds))
  }

  override fun setFadeOutDuration(duration: Duration) {
    messagingChannel.send(SetSoundFadeOutDurationEvent(soundId, duration.inWholeMilliseconds))
  }

  override fun setPremiumSegmentsEnabled(enabled: Boolean) {
    messagingChannel.send(EnableSoundPremiumSegmentsEvent(soundId, enabled))
  }

  override fun setAudioBitrate(bitrate: String) {
    messagingChannel.send(SetSoundAudioBitrateEvent(soundId, bitrate))
  }

  override fun setAudioAttributes(attrs: AudioAttributes) {
    // no-op
  }

  override fun setVolume(volume: Float) {
    messagingChannel.send(SetSoundVolumeEvent(soundId, volume))
  }

  override fun play() {
    messagingChannel.send(PlaySoundEvent(soundId))
  }

  override fun pause(immediate: Boolean) {
    messagingChannel.send(PauseSoundEvent(soundId, immediate))
  }

  override fun stop(immediate: Boolean) {
    messagingChannel.send(StopSoundEvent(soundId, immediate))
  }

  override fun onEventReceived(event: Event) {
    if (event !is SoundStateChangedEvent || event.soundId != soundId) {
      return
    }

    state = when (event.state) {
      "buffering" -> State.BUFFERING
      "playing" -> State.PLAYING
      "pausing" -> State.PAUSING
      "paused" -> State.PAUSED
      "stopping" -> State.STOPPING
      "stopped" -> State.STOPPED
      else -> throw IllegalArgumentException("unknown sound state: ${event.state}")
    }

    if (state == State.STOPPED) {
      messagingChannel.removeEventListener(this)
    }
  }

  class Factory(private val messagingChannel: CastMessagingChannel) : SoundPlayer.Factory {

    override fun buildPlayer(soundId: String): SoundPlayer {
      return CastSoundPlayer(soundId, messagingChannel)
    }
  }
}

package com.github.ashutoshgngwr.noice.cast

import com.github.ashutoshgngwr.noice.cast.models.GlobalUiUpdatedEvent
import com.github.ashutoshgngwr.noice.cast.models.PresetNameUpdatedEvent
import com.github.ashutoshgngwr.noice.cast.models.SoundUiUpdatedEvent
import com.github.ashutoshgngwr.noice.engine.SoundPlayer
import com.github.ashutoshgngwr.noice.engine.SoundPlayerManager

class DefaultCastReceiverUiManager(private val messagingChannel: CastMessagingChannel) :
  CastReceiverUiManager {

  override fun setSoundPlayerManagerState(state: SoundPlayerManager.State, volume: Float) {
    val stateStr = when (state) {
      SoundPlayerManager.State.PLAYING -> "playing"
      SoundPlayerManager.State.PAUSING -> "pausing"
      SoundPlayerManager.State.PAUSED -> "paused"
      SoundPlayerManager.State.STOPPING -> "stopping"
      SoundPlayerManager.State.STOPPED -> "stopped"
    }

    messagingChannel.send(GlobalUiUpdatedEvent(stateStr, volume))
  }

  override fun setSoundPlayerState(soundId: String, state: SoundPlayer.State, volume: Float) {
    val stateStr = when (state) {
      SoundPlayer.State.BUFFERING -> "buffering"
      SoundPlayer.State.STOPPED -> "stopped"
      else -> "playing"
    }

    messagingChannel.send(SoundUiUpdatedEvent(soundId, stateStr, volume))
  }

  override fun setPresetName(name: String?) {
    messagingChannel.send(PresetNameUpdatedEvent(name))
  }
}

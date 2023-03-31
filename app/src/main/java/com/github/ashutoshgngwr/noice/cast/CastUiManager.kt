package com.github.ashutoshgngwr.noice.cast

import com.github.ashutoshgngwr.noice.engine.SoundPlayer
import com.github.ashutoshgngwr.noice.engine.SoundPlayerManager

interface CastUiManager {
  fun setSoundPlayerManagerState(state: SoundPlayerManager.State, volume: Float)
  fun setSoundPlayerState(soundId: String, state: SoundPlayer.State, volume: Float)
  fun setPresetName(name: String?)
}

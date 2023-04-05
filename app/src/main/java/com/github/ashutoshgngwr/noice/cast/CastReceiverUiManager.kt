package com.github.ashutoshgngwr.noice.cast

import com.github.ashutoshgngwr.noice.engine.SoundPlayer
import com.github.ashutoshgngwr.noice.engine.SoundPlayerManager

/**
 * An interface declaring the UI update operations supported by the Cast receiver application.
 */
interface CastReceiverUiManager {
  /**
   * Sets the main playback state and the volume to the given values on the cast receiver
   * application.
   */
  fun setSoundPlayerManagerState(state: SoundPlayerManager.State, volume: Float)

  /**
   * Sets the sound playback state the volume to the given values for the corresponding sound on the
   * cast receiver application.
   */
  fun setSoundPlayerState(soundId: String, state: SoundPlayer.State, volume: Float)

  /**
   * Sets the currently playing preset name to the given value on the cast receiver application.
   */
  fun setPresetName(name: String?)
}

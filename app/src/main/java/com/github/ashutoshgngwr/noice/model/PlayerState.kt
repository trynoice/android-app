package com.github.ashutoshgngwr.noice.model

import com.github.ashutoshgngwr.noice.engine.PlaybackState
import com.google.gson.annotations.Expose
import java.io.Serializable

/**
 * Represents the state of a sound's player instance at any given time. Implements a custom [equals]
 * method to ignore [playbackState] in equality checks.
 */
data class PlayerState(
  @Expose val soundId: String,
  @Expose val volume: Int,
  @Transient val playbackState: PlaybackState = PlaybackState.IDLE,
) : Serializable {

  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }

    if (other !is PlayerState) {
      return false
    }

    // ignore playback state in equality.
    return soundId == other.soundId && volume == other.volume
  }

  override fun hashCode(): Int {
    // auto-generated
    var result = soundId.hashCode()
    result = 31 * result + volume
    return result
  }
}

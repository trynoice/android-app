package com.github.ashutoshgngwr.noice.sound

/**
 * PlaybackControlEvents defines a set of events subscribed by the [PlaybackManager].
 * Publish these events to control playback of sounds in [PlaybackManager].
 */
class PlaybackControlEvents {
  data class StartPlaybackEvent(val soundKey: String?) {
    constructor() : this(null)
  }

  data class StopPlaybackEvent(val soundKey: String?) {
    constructor() : this(null)
  }

  data class UpdatePlaybackEvent(val playback: Playback)
  class PausePlaybackEvent
}

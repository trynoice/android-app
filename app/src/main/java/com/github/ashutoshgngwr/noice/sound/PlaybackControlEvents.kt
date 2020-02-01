package com.github.ashutoshgngwr.noice.sound

class PlaybackControlEvents {

  class StartPlaybackEvent(val soundKey: String?)
  class StopPlaybackEvent(val soundKey: String?)
  class UpdatePlaybackEvent(val playback: Playback)
}

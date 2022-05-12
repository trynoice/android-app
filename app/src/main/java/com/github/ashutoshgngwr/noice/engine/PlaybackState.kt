package com.github.ashutoshgngwr.noice.engine

/**
 * Represents various lifecycle states of a [Player] and [PlayerManager] instance. A [Player]
 * instance uses all of these states in its lifecycle, while a [PlayerManager] starts at [STOPPED]
 * and never transitions to the [IDLE], [BUFFERING] and [FAILED] states.
 */
enum class PlaybackState {
  IDLE, BUFFERING, PLAYING, PAUSING, PAUSED, STOPPING, STOPPED, FAILED,
}

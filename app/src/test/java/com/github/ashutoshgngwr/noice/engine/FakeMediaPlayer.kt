package com.github.ashutoshgngwr.noice.engine

import androidx.media.AudioAttributesCompat
import kotlin.time.Duration

class FakeMediaPlayer : MediaPlayer() {

  var audioAttributes: AudioAttributesCompat? = null; private set
  var volume = 1F; private set
  var pendingFadeTransition: FadeTransition? = null; private set

  private val playlist = mutableListOf<String>()

  override fun setAudioAttributes(attrs: AudioAttributesCompat) {
    audioAttributes = attrs
  }

  override fun setVolume(volume: Float) {
    this.volume = volume
  }

  override fun play() {
    state = if (playlist.isNotEmpty()) State.BUFFERING else State.IDLE
  }

  override fun pause() {
    state = State.PAUSED
  }

  override fun stop() {
    state = State.STOPPED
  }

  override fun addToPlaylist(uri: String) {
    playlist.add(uri)
    if (playlist.size == 1) {
      listener?.onMediaPlayerItemTransition()
    }
  }

  override fun clearPlaylist() {
    playlist.clear()
    state = State.IDLE
    listener?.onMediaPlayerItemTransition()
  }

  override fun getRemainingItemCount(): Int {
    return playlist.size
  }

  override fun fadeTo(toVolume: Float, duration: Duration, callback: () -> Unit) {
    pendingFadeTransition = FadeTransition(volume, toVolume, duration, callback)
  }

  fun setStateTo(state: State) {
    this.state = state
  }

  fun consumePendingFadeTransition() {
    pendingFadeTransition?.also { transition ->
      volume = transition.toVolume
      transition.callback.invoke()
    }

    pendingFadeTransition = null
  }

  fun nextPlaylistItem(): String? {
    return playlist.firstOrNull()
  }

  fun consumeNextPlaylistItem() {
    if (playlist.removeFirstOrNull() != null) {
      listener?.onMediaPlayerItemTransition()
    }

    if (playlist.isEmpty()) {
      state = State.IDLE
    }
  }

  data class FadeTransition(
    val fromVolume: Float,
    val toVolume: Float,
    val duration: Duration,
    val callback: () -> Unit,
  )
}

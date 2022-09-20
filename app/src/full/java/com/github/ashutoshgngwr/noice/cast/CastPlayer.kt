package com.github.ashutoshgngwr.noice.cast

import android.util.Log
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.engine.PlaybackState
import com.github.ashutoshgngwr.noice.engine.Player
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.CoroutineScope

class CastPlayer private constructor(
  soundId: String,
  audioBitrate: String,
  soundRepository: SoundRepository,
  externalScope: CoroutineScope,
  playbackListener: PlaybackListener,
  override var audioAttributes: AudioAttributesCompat,
) : Player(soundId, audioBitrate, soundRepository, externalScope, playbackListener) {

  override fun resetSegmentQueue() {
    Log.i(LOG_TAG, "resetSegmentQueue:")
    playInternal()
  }

  override fun playInternal() {
    Log.i(LOG_TAG, "playInternal:")
    setPlaybackState(PlaybackState.PLAYING)
  }

  override fun pause(immediate: Boolean) {
    Log.i(LOG_TAG, "pause:")
    setPlaybackState(PlaybackState.PAUSED)
  }

  override fun stop(immediate: Boolean) {
    Log.i(LOG_TAG, "stop:")
    setPlaybackState(PlaybackState.STOPPED)
  }

  override fun setVolumeInternal(volume: Float) {
    Log.i(LOG_TAG, "setVolumeInternal: $volume")
  }

  override fun onSegmentAvailable(uri: String) {
    Log.i(LOG_TAG, "onSegmentAvailable: $uri")
  }

  companion object {
    private const val LOG_TAG = "CastPlayer"
  }

  class Factory(castContext: CastContext, channelId: String) : Player.Factory {

    override fun createPlayer(
      soundId: String,
      soundRepository: SoundRepository,
      audioBitrate: String,
      audioAttributes: AudioAttributesCompat,
      defaultScope: CoroutineScope,
      playbackListener: PlaybackListener
    ): Player {
      return CastPlayer(
        soundId,
        audioBitrate,
        soundRepository,
        defaultScope,
        playbackListener,
        audioAttributes,
      )
    }
  }
}

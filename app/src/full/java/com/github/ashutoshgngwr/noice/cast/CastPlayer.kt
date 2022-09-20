package com.github.ashutoshgngwr.noice.cast

import androidx.media.AudioAttributesCompat
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
    TODO("Not yet implemented")
  }

  override fun playInternal() {
    TODO("Not yet implemented")
  }

  override fun pause(immediate: Boolean) {
    TODO("Not yet implemented")
  }

  override fun stop(immediate: Boolean) {
    TODO("Not yet implemented")
  }

  override fun setVolumeInternal(volume: Float) {
    TODO("Not yet implemented")
  }

  override fun onSegmentAvailable(uri: String) {
    TODO("Not yet implemented")
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

package com.github.ashutoshgngwr.noice.playback.strategy

import android.content.Context
import android.net.Uri
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.ext.fade
import com.github.ashutoshgngwr.noice.ext.setAudioAttributesCompat
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util

/**
 * [LocalPlaybackStrategy] implements [PlaybackStrategy] which plays the media locally
 * on device using the [SimpleExoPlayer] implementation.
 */
class LocalPlaybackStrategy(
  context: Context,
  audioAttributes: AudioAttributesCompat,
  sound: Sound
) : PlaybackStrategy {

  companion object {
    // a smaller default used when changing volume of an active player.
    internal const val DEFAULT_FADE_DURATION = 1000L
  }

  private val players = sound.src.map { initPlayer(context, it, sound.isLooping) }
  private val settingsRepository = SettingsRepository.newInstance(context)
  private var volume: Float = 0f

  init {
    setAudioAttributes(audioAttributes)
  }

  private fun initPlayer(context: Context, src: String, isLooping: Boolean): SimpleExoPlayer {
    return SimpleExoPlayer.Builder(context)
      .build()
      .apply {
        repeatMode = if (isLooping) {
          ExoPlayer.REPEAT_MODE_ONE
        } else {
          ExoPlayer.REPEAT_MODE_OFF
        }

        prepare(
          Util.getUserAgent(context, context.packageName).let { userAgent ->
            ProgressiveMediaSource.Factory(DefaultDataSourceFactory(context, userAgent))
              .createMediaSource(Uri.parse("asset:///$src"))
          }
        )
      }
  }

  override fun setVolume(volume: Float) {
    this.volume = volume
    players.forEach { it.fade(it.volume, volume, duration = DEFAULT_FADE_DURATION) }
  }

  override fun play() {
    for (player in players) {
      if (player.repeatMode != ExoPlayer.REPEAT_MODE_ONE && !player.isPlaying) {
        player.seekTo(0)
      }

      player.playWhenReady = true
      // an internal feature of the LocalPlaybackStrategy is that it won't fade-in non-looping sounds
      if (player.repeatMode == ExoPlayer.REPEAT_MODE_ONE) {
        player.fade(0f, volume, settingsRepository.getSoundFadeInDurationMillis())
      }
    }
  }

  override fun pause() {
    players.forEach {
      it.fade(it.volume, 0f, duration = DEFAULT_FADE_DURATION) {
        it.playWhenReady = false
      }
    }
  }

  override fun stop() {
    for (player in players) {
      if (!player.playWhenReady) {
        continue
      }

      player.fade(player.volume, 0f, DEFAULT_FADE_DURATION) {
        player.playWhenReady = false
        player.release()
      }
    }
  }

  override fun setAudioAttributes(attrs: AudioAttributesCompat) {
    players.forEach { it.setAudioAttributesCompat(attrs) }
  }
}

package com.github.ashutoshgngwr.noice.playback.strategy

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.os.HandlerCompat
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

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
    private const val FADE_VOLUME_STEP = 0.01f

    // a smaller default used when changing volume of an active player.
    private const val VOLUME_ADJUSTMENT_FADE_DURATION = 750L
  }

  private val handler = Handler(Looper.getMainLooper())
  private val players = sound.src.map { initPlayer(context, it, sound.isLooping, audioAttributes) }
  private val settingsRepository = SettingsRepository.newInstance(context)
  private var volume: Float = 0f

  private fun initPlayer(
    context: Context,
    src: String,
    isLooping: Boolean,
    audioAttributes: AudioAttributesCompat
  ): SimpleExoPlayer {
    return SimpleExoPlayer.Builder(context)
      .build()
      .apply {
        repeatMode = if (isLooping) {
          ExoPlayer.REPEAT_MODE_ONE
        } else {
          ExoPlayer.REPEAT_MODE_OFF
        }

        setAudioAttributes(
          AudioAttributes.Builder()
            .setContentType(audioAttributes.contentType)
            .setFlags(audioAttributes.flags)
            .setUsage(audioAttributes.usage)
            .build(),
          false
        )

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
    players.forEach { it.fade(it.volume, volume, duration = VOLUME_ADJUSTMENT_FADE_DURATION) }
  }

  override fun play() {
    for (player in players) {
      if (player.repeatMode != ExoPlayer.REPEAT_MODE_ONE && !player.isPlaying) {
        player.seekTo(0)
      }

      player.playWhenReady = true
      // an internal feature of the LocalPlaybackStrategy is that it won't fade-in non-looping sounds
      if (player.repeatMode == ExoPlayer.REPEAT_MODE_ONE) {
        player.fade(0f, volume)
      }
    }
  }

  override fun pause() {
    players.forEach {
      it.fade(it.volume, 0f, duration = VOLUME_ADJUSTMENT_FADE_DURATION) {
        it.playWhenReady = false
      }
    }
  }

  override fun stop() {
    for (player in players) {
      if (!player.playWhenReady) {
        continue
      }

      player.fade(player.volume, 0f) {
        player.playWhenReady = false
        player.release()
      }
    }
  }

  /**
   * an extension to [SimpleExoPlayer] for fade in and out effects
   *
   * @param fromVolume initial volume where the fade transition will start
   * @param toVolume final volume where the fade transition will end
   * @param duration duration of fade in ms
   * @param callback callback when fade transition finishes
   */
  private fun SimpleExoPlayer.fade(
    fromVolume: Float,
    toVolume: Float,
    duration: Long = settingsRepository.getSoundFadeDurationInMillis(),
    callback: () -> Unit = { }
  ) {
    handler.removeCallbacksAndMessages(this)
    if (!playWhenReady && !isPlaying) {
      // edge case where fade is requested but playback is not playing.
      volume = toVolume
      callback.invoke()
      return
    }

    val sign = if (toVolume > fromVolume) 1 else -1
    val steps = 1 + ceil(abs(toVolume - fromVolume) / FADE_VOLUME_STEP).toInt()
    val period = duration / steps
    volume = fromVolume
    for (i in 0 until steps) {
      HandlerCompat.postDelayed(
        handler, { volume = min(1f, volume + (sign * FADE_VOLUME_STEP)) }, this, i * period
      )
    }

    HandlerCompat.postDelayed(handler, {
      volume = toVolume
      callback.invoke()
    }, this, duration + 1)
  }
}

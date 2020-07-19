package com.github.ashutoshgngwr.noice.sound.player.strategy

import android.content.Context
import android.net.Uri
import android.os.CountDownTimer
import androidx.annotation.VisibleForTesting
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.sound.Sound
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.AssetDataSource
import com.google.android.exoplayer2.upstream.DataSource
import kotlin.math.abs
import kotlin.math.ceil

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
    const val FADE_VOLUME_STEP = 0.02f
    const val FADE_DURATION = 750L
  }

  private val exoPlayer = initPlayer(context, sound, audioAttributes)

  // global reference is needed if volume is tweaked during an ongoing transition
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  var transitionTicker: CountDownTimer? = null

  private fun initPlayer(
    context: Context,
    sound: Sound,
    audioAttributes: AudioAttributesCompat
  ): SimpleExoPlayer {
    return SimpleExoPlayer.Builder(context)
      .build()
      .apply {
        repeatMode = if (sound.isLooping) {
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
          ProgressiveMediaSource.Factory(DataSource.Factory { AssetDataSource(context) })
            .createMediaSource(Uri.parse("asset:///${sound.key}.mp3"))
        )
      }
  }

  override fun setVolume(volume: Float) {
    transitionTicker?.cancel()
    exoPlayer.fade(exoPlayer.volume, volume, FADE_DURATION)
  }

  override fun play() {
    if (exoPlayer.playWhenReady && exoPlayer.isPlaying) {
      return
    }

    // an internal feature of the LocalPlaybackStrategy is that it won't fade-in non-looping sounds
    if (exoPlayer.repeatMode == ExoPlayer.REPEAT_MODE_ONE) {
      exoPlayer.playWhenReady = true
      exoPlayer.fade(0f, exoPlayer.volume, FADE_DURATION)
    } else {
      exoPlayer.seekTo(0)
      exoPlayer.playWhenReady = true
    }
  }

  override fun pause() {
    exoPlayer.playWhenReady = false
  }

  override fun stop() {
    if (!exoPlayer.playWhenReady) {
      return
    }

    exoPlayer.fade(exoPlayer.volume, 0f, FADE_DURATION) {
      playWhenReady = false
      release()
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
    duration: Long,
    callback: SimpleExoPlayer.() -> Unit = { }
  ) {
    if (!playWhenReady && !isPlaying) {
      // edge case where fade is requested but playback is not playing.
      volume = toVolume
      callback.invoke(this)
      return
    }

    val sign = if (toVolume > fromVolume) 1 else -1
    val steps = 1 + ceil(abs(toVolume - fromVolume) / FADE_VOLUME_STEP).toInt()
    val period = duration / steps
    volume = fromVolume
    transitionTicker?.cancel()
    transitionTicker = object : CountDownTimer(FADE_DURATION, period) {
      override fun onFinish() {
        volume = toVolume
        transitionTicker = null
        callback()
      }

      override fun onTick(millisUntilFinished: Long) {
        volume += sign * FADE_VOLUME_STEP
      }
    }.also { it.start() }
  }
}

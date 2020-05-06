package com.github.ashutoshgngwr.noice.sound.player

import android.content.Context
import android.net.Uri
import android.os.CountDownTimer
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.sound.Sound
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.AssetDataSource
import com.google.android.exoplayer2.upstream.DataSource
import kotlin.math.ceil

/**
 * [LocalSoundPlayer] implements [SoundPlayer] which plays the media locally
 * on device using the [SimpleExoPlayer] implementation.
 */
class LocalSoundPlayer(context: Context, audioAttributes: AudioAttributesCompat, sound: Sound) :
  SoundPlayer() {

  companion object {
    const val FADE_VOLUME_STEP = 0.02f
    const val FADE_DURATION = 1000L
  }

  private val exoPlayer = initPlayer(context, sound, audioAttributes)

  // global references are needed if volume is tweaked during an ongoing transition
  private var transitionTicker: CountDownTimer? = null

  private fun initPlayer(
    context: Context,
    sound: Sound,
    audioAttributes: AudioAttributesCompat
  ): SimpleExoPlayer {
    return SimpleExoPlayer.Builder(context)
      .build()
      .apply {
        repeatMode = if (sound.isLoopable) {
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
    exoPlayer.volume = volume
  }

  override fun play() {
    if (exoPlayer.playWhenReady) {
      return
    }

    // an internal feature of the LocalPlayer is that it won't fade-in non-looping sounds
    if (exoPlayer.repeatMode == ExoPlayer.REPEAT_MODE_ONE) {
      exoPlayer.fade(1, FADE_DURATION)
    } else {
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

    exoPlayer.fade(-1, FADE_DURATION) {
      playWhenReady = false
      release()
    }
  }

  /**
   * an extension to [SimpleExoPlayer] for fade in and out effects
   *
   * @param sign direction of fade. 1 for in, -1 for out
   * @param duration duration of fade in ms
   * @param callback callback when fade transition finishes
   */
  private fun SimpleExoPlayer.fade(
    sign: Short,
    duration: Long,
    callback: SimpleExoPlayer.() -> Unit = { }
  ) {
    if (sign < 0 && !playWhenReady) {
      // special case where fade-out is requested but playback is not playing.
      return
    }

    val steps = ceil(volume / FADE_VOLUME_STEP).toInt()
    val period = duration / steps
    var to = 0.0f
    var from = 0.0f
    if (sign < 0) {
      from = volume
    } else {
      to = volume
    }

    volume = from
    exoPlayer.playWhenReady = true
    transitionTicker?.cancel()
    transitionTicker = object : CountDownTimer(FADE_DURATION, period) {
      override fun onFinish() {
        volume = to
        transitionTicker = null
        callback()
      }

      override fun onTick(millisUntilFinished: Long) {
        volume += sign * FADE_VOLUME_STEP
      }

    }.also { it.start() }
  }
}

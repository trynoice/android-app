package com.github.ashutoshgngwr.noice.ext

import android.os.Handler
import android.os.Looper
import androidx.core.os.HandlerCompat
import androidx.media.AudioAttributesCompat
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min


private const val FADE_CALLBACK_TOKEN = "SimpleExoPlayer.fadeCallbackToken"
private const val FADE_VOLUME_STEP = 0.005f

/**
 * Fade in and out effect for [SimpleExoPlayer].
 *
 * @param fromVolume initial volume where the fade transition will start
 * @param toVolume final volume where the fade transition will end
 * @param duration duration of fade in ms
 * @param callback callback when fade transition finishes
 */
fun SimpleExoPlayer.fade(
  fromVolume: Float,
  toVolume: Float,
  duration: Long,
  callback: (() -> Unit)? = null
) {
  // TODO: the code works for current requirements, but it is problematic. e.g. it cancels the
  //  previous transition (if on-going) when a new one is scheduled, thus also cancelling its
  //  callback. This behaviour might not be desired.

  val handler = Handler(Looper.getMainLooper())
  handler.removeCallbacksAndMessages(FADE_CALLBACK_TOKEN)
  if (!playWhenReady && !isPlaying) {
    // edge case where fade is requested but playback is not playing.
    volume = toVolume
    callback?.invoke()
    return
  }

  val sign = if (toVolume > fromVolume) 1 else -1
  val steps = 1 + ceil(abs(toVolume - fromVolume) / FADE_VOLUME_STEP).toInt()
  val period = duration / steps
  volume = fromVolume
  for (i in 0 until steps) {
    HandlerCompat.postDelayed(
      handler,
      { volume = min(1f, volume + (sign * FADE_VOLUME_STEP)) },
      this,
      i * period
    )
  }

  HandlerCompat.postDelayed(handler, {
    volume = toVolume
    callback?.invoke()
  }, FADE_CALLBACK_TOKEN, duration + 1)
}

/**
 * Implicitly translates [AudioAttributesCompat] to [AudioAttributes] and sets them on the provided
 * [SimpleExoPlayer] receiver instance.
 */
fun SimpleExoPlayer.setAudioAttributesCompat(
  attrs: AudioAttributesCompat,
  handleAudioFocus: Boolean
) {
  setAudioAttributes(
    AudioAttributes.Builder()
      .setContentType(attrs.contentType)
      .setFlags(attrs.flags)
      .setUsage(attrs.usage)
      .build(),
    handleAudioFocus,
  )
}

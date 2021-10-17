package com.github.ashutoshgngwr.noice.ext

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import androidx.core.os.HandlerCompat
import androidx.media.AudioAttributesCompat
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min


private const val FADE_VOLUME_STEP = 0.001f
private val handler = Handler(Looper.getMainLooper())

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

  handler.removeCallbacksAndMessages(this)
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
      { volume = max(0f, min(1f, volume + (sign * FADE_VOLUME_STEP))) },
      this,
      i * period
    )
  }

  HandlerCompat.postDelayed(handler, {
    volume = max(0f, min(1f, toVolume))
    callback?.invoke()
  }, this, duration + 50)
}

/**
 * Implicitly translates [AudioAttributesCompat] to [AudioAttributes] and sets them on the provided
 * [SimpleExoPlayer] receiver instance.
 */
fun SimpleExoPlayer.setAudioAttributesCompat(compatAttrs: AudioAttributesCompat) {
  // internally, both implementations borrow their constants from `android.media`.
  @SuppressLint("WrongConstant")
  val attrs = AudioAttributes.Builder()
    .setContentType(compatAttrs.contentType)
    .setFlags(compatAttrs.flags)
    .setUsage(compatAttrs.usage)
    .build()

  if (attrs != audioAttributes) {
    setAudioAttributes(attrs, false)
  }
}

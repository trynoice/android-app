package com.github.ashutoshgngwr.noice.sound

import android.content.Context
import android.net.Uri
import android.os.Handler
import androidx.annotation.VisibleForTesting
import androidx.media.AudioAttributesCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.AssetDataSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.gson.*
import com.google.gson.annotations.Expose
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type
import kotlin.random.Random.Default.nextInt

/**
 * Playback manages playback of a single [Sound]. It holds reference to underlying
 * [MediaPlayer][android.media.MediaPlayer] instance along with playback information such
 * as isPlaying, volume and timePeriod.
 */
class Playback(
  context: Context,
  sound: Sound,
  audioAttributes: AudioAttributesCompat
) : Runnable {

  companion object {
    const val DEFAULT_VOLUME = 4
    const val MAX_VOLUME = 20
    const val DEFAULT_TIME_PERIOD = 30
    private const val MIN_TIME_PERIOD = 30
    const val MAX_TIME_PERIOD = 240
    private const val TRANSITION_VOLUME_STEP = 0.01f // player's volume. not playback's
    private const val TRANSITION_STEP_DELAY = 25L
  }

  /**
   * VolumeSerializer is a fix for maintaining backward compatibility with versions older than
   * 0.3.0. Volume was written as a Float to persistent storage in older versions.
   * Switching to Integer in newer version was causing crash if the user had any saved presets.
   */
  private inner class VolumeSerializer : JsonSerializer<Int>, JsonDeserializer<Int> {
    override fun serialize(
      src: Int?,
      typeOfSrc: Type?,
      context: JsonSerializationContext?
    ): JsonElement {
      return JsonPrimitive(
        if (src == null) {
          (DEFAULT_VOLUME.toFloat() / MAX_VOLUME)
        } else {
          src.toFloat() / MAX_VOLUME
        }
      )
    }

    override fun deserialize(
      json: JsonElement?,
      typeOfT: Type?,
      context: JsonDeserializationContext?
    ): Int {
      return if (json == null) {
        DEFAULT_VOLUME
      } else {
        (json.asFloat * MAX_VOLUME).toInt()
      }
    }

  }

  // curious about the weird serialized names? see https://github.com/ashutoshgngwr/noice/issues/110
  // and https://github.com/ashutoshgngwr/noice/pulls/117
  @Expose
  @SerializedName("b")
  @JsonAdapter(value = VolumeSerializer::class)
  var volume = DEFAULT_VOLUME
    private set

  @Expose
  @SerializedName("c")
  var timePeriod = DEFAULT_TIME_PERIOD

  var isPlaying = false
    private set

  @Expose
  @SerializedName("a")
  val soundKey = sound.key

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  val player = createPlayer(context, audioAttributes, sound)

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  val handler = Handler()

  private var isTransitioning = false

  // initializes a MediaPlayer object with the passed arguments.
  private fun createPlayer(
    context: Context,
    audioAttributes: AudioAttributesCompat,
    sound: Sound
  ): SimpleExoPlayer {
    return SimpleExoPlayer.Builder(context)
      .build()
      .apply {
        volume = DEFAULT_VOLUME.toFloat() / MAX_VOLUME
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

  private fun fadeIn() {
    if (player.volume >= volume.toFloat() / MAX_VOLUME) {
      isTransitioning = false
      return
    }

    isTransitioning = true
    player.volume += TRANSITION_VOLUME_STEP
    handler.postDelayed(this::fadeIn, TRANSITION_STEP_DELAY)
  }

  /**
   * Sets the volume for the playback. It also sets the volume of the underlying
   * [MediaPlayer][android.media.MediaPlayer] instance.
   */
  fun setVolume(volume: Int) {
    this.volume = volume
    if (!isTransitioning) {
      player.volume = volume.toFloat() / MAX_VOLUME
    }
  }

  /**
   * Starts playing the sound. If the sound is not loopable, it also schedules a delayed
   * task to replay the sound. Delay period is randomised with guaranteed
   * [MIN_TIME_PERIOD][MIN_TIME_PERIOD].
   */
  fun play() {
    isPlaying = true
    if (player.repeatMode == ExoPlayer.REPEAT_MODE_ONE) {
      player.volume = 0f
      player.playWhenReady = true
      fadeIn()
    } else {
      run()
    }
  }

  /**
   * Implements the randomised play callback for non-looping sounds.
   */
  override fun run() {
    if (!isPlaying) {
      return
    }

    player.seekTo(0)
    player.playWhenReady = true
    handler.postDelayed(this, (MIN_TIME_PERIOD + nextInt(0, timePeriod)) * 1000L)
  }

  /**
   * Stops the playback. If the sound is non-loopable, it also removes the randomised play callback.
   */
  fun stop() {
    isPlaying = false
    player.playWhenReady = false

    if (player.repeatMode == ExoPlayer.REPEAT_MODE_OFF) {
      handler.removeCallbacks(this)
    }
  }

  /**
   * Releases resources used by underlying [MediaPlayer][android.media.MediaPlayer] instance.
   */
  fun release() {
    stop()
    player.release()
  }

  /**
   * Custom implementation of equals is required for comparing playback states
   * in comparing saved Presets
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }

    if (javaClass != other?.javaClass) {
      return false
    }

    other as Playback
    return soundKey == other.soundKey && volume == other.volume && timePeriod == other.timePeriod
  }

  /**
   * auto-generated
   */
  override fun hashCode(): Int {
    var result = volume
    result = 31 * result + timePeriod
    result = 31 * result + soundKey.hashCode()
    return result
  }
}

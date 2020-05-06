package com.github.ashutoshgngwr.noice.sound

import android.os.Handler
import com.github.ashutoshgngwr.noice.sound.player.SoundPlayerFactory
import com.google.gson.*
import com.google.gson.annotations.Expose
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type
import kotlin.random.Random.Default.nextInt

/**
 * Playback manages playback of a single [Sound]. It holds reference to underlying
 * [SoundPlayer][com.github.ashutoshgngwr.noice.sound.player.SoundPlayer] instance
 * along with playback information such as [isPlaying], [volume] and [timePeriod].
 */
class Playback(private val sound: Sound, soundPlayerFactory: SoundPlayerFactory) : Runnable {

  companion object {
    const val DEFAULT_VOLUME = 4
    const val MAX_VOLUME = 20
    const val DEFAULT_TIME_PERIOD = 30
    private const val MIN_TIME_PERIOD = 30
    const val MAX_TIME_PERIOD = 240
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

  private var player = soundPlayerFactory.newPlayer(sound).also {
    it.setVolume(volume.toFloat() / MAX_VOLUME)
  }

  private val handler = Handler()

  /**
   * Sets the volume for the playback. It also sets the volume of the underlying
   * [SoundPlayer][com.github.ashutoshgngwr.noice.sound.player.SoundPlayer] instance.
   */
  fun setVolume(volume: Int) {
    this.volume = volume
    this.player.setVolume(volume.toFloat() / MAX_VOLUME)
  }

  /**
   * Starts playing the sound. If the sound is not loopable, it also schedules a delayed
   * task to replay the sound. Delay period is randomised with guaranteed
   * [MIN_TIME_PERIOD][MIN_TIME_PERIOD].
   */
  fun play() {
    isPlaying = true
    if (sound.isLoopable) {
      player.play()
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

    player.play()
    handler.postDelayed(this, (MIN_TIME_PERIOD + nextInt(0, timePeriod)) * 1000L)
  }

  /**
   * Stops the playback without releasing the underlying media resource.
   * If the sound is non-loopable, it also removes the randomised play callback.
   */
  fun pause() {
    isPlaying = false
    player.pause()
    if (sound.isLoopable) {
      handler.removeCallbacks(this)
    }
  }

  /**
   * Stops the playback and releases the underlying media resource.
   * If the sound is non-loopable, it also removes the randomised play callback.
   */
  fun stop() {
    isPlaying = false
    player.stop()
    if (sound.isLoopable) {
      handler.removeCallbacks(this)
    }
  }

  /**
   * recreates the underlying [SoundPlayer][com.github.ashutoshgngwr.noice.sound.player.SoundPlayer]
   * using the provided [SoundPlayerFactory].
   */
  fun recreatePlayerWithFactory(factory: SoundPlayerFactory) {
    // pause then stop just to prevent the fade-out transition from LocalSoundPlayer
    player.pause()
    player.stop()
    player = factory.newPlayer(sound).also {
      it.setVolume(volume.toFloat() / MAX_VOLUME)
      it.play()
    }
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

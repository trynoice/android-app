package com.github.ashutoshgngwr.noice.sound.player

import android.os.Handler
import com.github.ashutoshgngwr.noice.sound.Sound
import com.github.ashutoshgngwr.noice.sound.player.adapter.PlayerAdapter
import com.github.ashutoshgngwr.noice.sound.player.adapter.PlayerAdapterFactory
import com.google.gson.*
import com.google.gson.annotations.Expose
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type
import kotlin.random.Random.Default.nextInt

/**
 * [Player] manages playback of a single [Sound]. It [PlayerAdapter] instances to control media
 * playback and keeps track of playback information such as [isPlaying], [volume] and [timePeriod].
 */
class Player(private val sound: Sound, playerAdapterFactory: PlayerAdapterFactory) {

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

  private var playerAdapter = playerAdapterFactory.newPlayerAdapter(sound).also {
    it.setVolume(volume.toFloat() / MAX_VOLUME)
  }

  private val handler = Handler()

  /**
   * Sets the volume for the [Player] using current [PlayerAdapter].
   */
  fun setVolume(volume: Int) {
    this.volume = volume
    this.playerAdapter.setVolume(volume.toFloat() / MAX_VOLUME)
  }

  /**
   * Starts playing the sound. If the sound is not loopable, it also schedules a delayed
   * task to replay the sound. Delay period is randomised with guaranteed
   * [MIN_TIME_PERIOD][MIN_TIME_PERIOD].
   */
  fun play() {
    isPlaying = true
    if (sound.isLoopable) {
      playerAdapter.play()
    } else {
      playAndRegisterDelayedCallback()
    }
  }

  /**
   * Implements the randomised play callback for non-looping sounds.
   */
  private fun playAndRegisterDelayedCallback() {
    if (!isPlaying) {
      return
    }

    playerAdapter.play()
    val delay = (MIN_TIME_PERIOD + nextInt(0, timePeriod)) * 1000L
    handler.postDelayed(this::playAndRegisterDelayedCallback, delay)
  }

  /**
   * Stops the [Player] without releasing the underlying media resource.
   * If the sound is non-loopable, it also removes the randomised play callback.
   */
  fun pause() {
    isPlaying = false
    playerAdapter.pause()
    if (sound.isLoopable) {
      handler.removeCallbacks(this::playAndRegisterDelayedCallback)
    }
  }

  /**
   * Stops the [Player] and releases the underlying media resource.
   * If the sound is non-loopable, it also removes the randomised play callback.
   */
  fun stop() {
    isPlaying = false
    playerAdapter.stop()
    if (sound.isLoopable) {
      handler.removeCallbacks(this::playAndRegisterDelayedCallback)
    }
  }

  /**
   * setAdapter updates the [PlayerAdapter] used by the [Player] instance. All subsequent
   * player control commands are sent to the new [PlayerAdapter]. It also sends setVolume command
   * on the new [PlayerAdapter]. If [Player] is looping and playing, it also sends the play
   * command on the new [PlayerAdapter].
   */
  fun recreatePlayerAdapter(playerAdapterFactory: PlayerAdapterFactory) {
    // pause then stop just to prevent the fade-out transition from LocalSoundPlayer
    playerAdapter.pause()
    playerAdapter.stop()
    playerAdapter = playerAdapterFactory.newPlayerAdapter(sound).also {
      it.setVolume(volume.toFloat() / MAX_VOLUME)
      if (isPlaying && sound.isLoopable) { // because non looping will automatically play on scheduled callback.
        it.play()
      }
    }
  }

  /**
   * Custom implementation of equals is required for comparing [Player] states
   * in comparing saved Presets
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }

    if (javaClass != other?.javaClass) {
      return false
    }

    other as Player
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

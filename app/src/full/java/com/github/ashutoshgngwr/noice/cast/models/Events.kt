package com.github.ashutoshgngwr.noice.cast.models

import androidx.annotation.VisibleForTesting

abstract class Event {
  abstract val kind: String
}

object EventDeserializationRegistry {

  private val registry: MutableMap<String, Class<out Event>> = mutableMapOf(
    "GetAccessToken" to GetAccessTokenEvent::class.java,
    "SoundStateChanged" to SoundStateChangedEvent::class.java,
  )

  @VisibleForTesting
  fun registerType(kind: String, clazz: Class<out Event>) {
    registry[kind] = clazz
  }

  /**
   * @return the Java class of the model corresponding to the given [kind].
   * @throws IllegalArgumentException
   */
  fun getTypeForKind(kind: String): Class<out Event> {
    return registry[kind] ?: throw IllegalArgumentException("unknown event kind: $kind")
  }
}

data class GetAccessTokenEvent(override val kind: String = "GetAccessToken") : Event()

data class GetAccessTokenResponseEvent(
  val accessToken: String?,
  override val kind: String = "GetAccessTokenResponse",
) : Event()

data class SetSoundFadeInDurationEvent(
  val soundId: String,
  val durationMillis: Long,
  override val kind: String = "SetSoundFadeInDuration",
) : Event()

data class SetSoundFadeOutDurationEvent(
  val soundId: String,
  val durationMillis: Long,
  override val kind: String = "SetSoundFadeOutDuration",
) : Event()

data class EnableSoundPremiumSegmentsEvent(
  val soundId: String,
  val isEnabled: Boolean,
  override val kind: String = "EnableSoundPremiumSegments",
) : Event()

data class SetSoundAudioBitrateEvent(
  val soundId: String,
  val bitrate: String,
  override val kind: String = "SetSoundAudioBitrate",
) : Event()

data class SetSoundVolumeEvent(
  val soundId: String,
  val volume: Float,
  override val kind: String = "SetSoundVolume"
) : Event()

data class PlaySoundEvent(
  val soundId: String,
  override val kind: String = "PlaySound",
) : Event()

data class PauseSoundEvent(
  val soundId: String,
  val immediate: Boolean,
  override val kind: String = "PauseSound",
) : Event()

data class StopSoundEvent(
  val soundId: String,
  val immediate: Boolean,
  override val kind: String = "StopSound",
) : Event()

data class SoundStateChangedEvent(
  val soundId: String,
  val state: String,
  override val kind: String = "SoundStateChanged",
) : Event()

data class GlobalUiUpdatedEvent(
  val state: String,
  val volume: Float,
  override val kind: String = "GlobalUiUpdated",
) : Event()

data class SoundUiUpdatedEvent(
  val soundId: String,
  val state: String,
  val volume: Float,
  override val kind: String = "SoundUiUpdated",
) : Event()

data class PresetNameUpdatedEvent(
  val name: String?,
  override val kind: String = "PresetNameUpdated",
) : Event()

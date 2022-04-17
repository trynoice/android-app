package com.github.ashutoshgngwr.noice.model

import com.trynoice.api.client.models.SoundGroup
import com.trynoice.api.client.models.SoundSegment
import com.trynoice.api.client.models.SoundSource

/**
 * A thin wrapper around [API client's sound model][com.trynoice.api.client.models.Sound].
 *
 * @param id id of the sound.
 * @param group the [SoundGroup] that this [Sound] belongs to.
 * @param name a user-presentable name of the sound.
 * @param iconSvg a user-presentable icon of the sound as a SVG string.
 * @param maxSilence The upper limit (in seconds) for the amount of silence to add in-between
 * segments for non-contiguous sounds.
 * @param segmentsBasePath base path for the segments of this sound.
 * @param segments A list of segments for this sound.
 * @param sources A list of details attributing original clip sources, author and license.
 */
data class Sound(
  val id: String,
  val group: SoundGroup,
  val name: String,
  val iconSvg: String,
  val maxSilence: Int,
  val segmentsBasePath: String,
  val segments: List<SoundSegment>,
  val sources: List<SoundSource>,
) {

  /**
   * Whether the sound is contiguous or contains silences in-between.
   */
  val isLooping: Boolean
    get() = maxSilence == 0

  // TODO: remove this.
  enum class Tag {
    FOCUS, RELAX
  }
}

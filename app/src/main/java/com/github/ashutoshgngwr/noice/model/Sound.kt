package com.github.ashutoshgngwr.noice.model

import com.trynoice.api.client.models.SoundGroup
import com.trynoice.api.client.models.SoundSegment
import com.trynoice.api.client.models.SoundSource
import java.io.Serializable

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
) : Serializable {

  /**
   * Whether the sound is contiguous or contains silences in-between.
   */
  val isContiguous: Boolean
    get() = maxSilence == 0

  /**
   * Whether the sound contains non-free (premium) segments.
   */
  val hasPremiumSegments: Boolean
    get() = segments.any { !it.isFree }

  fun sourcesToMarkdown(): String {
    val markdownBuilder = StringBuilder()
    sources.forEachIndexed { i, source ->
      markdownBuilder.append("${i + 1}. [${source.name}](${source.url})")
      val author = source.author
      if (author != null) {
        markdownBuilder.append(" by [${author.name}](${author.url})")
      }

      val license = spdxListedLicenses[source.license]
      if (license != null) {
        markdownBuilder.append("  \n")
        markdownBuilder.append("   License: [${license.name}](${license.referenceUrl})")
      }

      markdownBuilder.append("\n")
    }

    return markdownBuilder.toString()
  }

  // TODO: remove this.
  enum class Tag {
    FOCUS, RELAX
  }

  companion object {
    private val spdxListedLicenses = mapOf(
      "CC-BY-3.0" to SpdxListedLicense(
        id = "CC-BY-3.0",
        name = "Creative Commons Attribution 3.0 Unported",
        referenceUrl = "https://spdx.org/licenses/CC-BY-3.0.html",
      ),
      "GPL-3.0-only" to SpdxListedLicense(
        id = "GPL-3.0-only",
        name = "GNU General Public License v3.0 only",
        referenceUrl = "https://spdx.org/licenses/GPL-3.0-only.html",
      ),
    )
  }

  private data class SpdxListedLicense(
    val id: String,
    val name: String,
    val referenceUrl: String,
  )
}

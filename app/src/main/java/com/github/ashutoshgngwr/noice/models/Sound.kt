package com.github.ashutoshgngwr.noice.models

import com.github.ashutoshgngwr.noice.data.models.SoundDto
import com.github.ashutoshgngwr.noice.data.models.SoundSourceDto

/**
 * @param id id of the sound.
 * @param group the [SoundGroup] that this [Sound] belongs to.
 * @param name a user-presentable name of the sound.
 * @param iconSvg a user-presentable icon of the sound as a SVG string.
 * @param maxSilence The upper limit (in seconds) for the amount of silence to add in-between
 * segments for non-contiguous sounds.
 * @param isPremium whether the sound is only available to Premium users.
 * @param segments A list of segments for this sound.
 * @param tags A list of tags that associate with this sound.
 * @param sources A list of details attributing original clip sources, author and license.
 */
data class Sound(
  val id: String,
  val group: SoundGroup,
  val name: String,
  val iconSvg: String,
  val maxSilence: Int,
  val isPremium: Boolean,
  val segments: List<SoundSegment>,
  val tags: List<SoundTag>,
  val sources: List<Source>,
) : java.io.Serializable {

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

  data class Source(
    val name: String,
    val url: String,
    val license: String,
    val author: SourceAuthor? = null,
  )

  data class SourceAuthor(
    val name: String,
    val url: String,
  )

  private data class SpdxListedLicense(
    val name: String,
    val referenceUrl: String,
  )

  companion object {
    private val spdxListedLicenses = mapOf(
      "CC-BY-3.0" to SpdxListedLicense(
        name = "Creative Commons Attribution 3.0 Unported",
        referenceUrl = "https://spdx.org/licenses/CC-BY-3.0.html",
      ),
      "CC-BY-4.0" to SpdxListedLicense(
        name = "Creative Commons Attribution 4.0 International",
        referenceUrl = "https://spdx.org/licenses/CC-BY-4.0.html",
      ),
      "GPL-3.0-only" to SpdxListedLicense(
        name = "GNU General Public License v3.0 only",
        referenceUrl = "https://spdx.org/licenses/GPL-3.0-only.html",
      ),
    )
  }
}

fun SoundDto.toDomainEntity(): Sound {
  return Sound(
    id = sound.id,
    group = group.toDomainEntity(),
    name = sound.name,
    iconSvg = sound.iconSvg,
    maxSilence = sound.maxSilence,
    isPremium = sound.isPremium,
    segments = segments.toDomainEntity(),
    tags = tags.toDomainEntity(),
    sources = sources.toDomainEntity(),
  )
}

@JvmName("toDomainEntitySoundDto")
fun List<SoundDto>.toDomainEntity(): List<Sound> {
  return map { it.toDomainEntity() }
}

@JvmName("toDomainEntitySoundSourceDto")
fun List<SoundSourceDto>.toDomainEntity(): List<Sound.Source> = map { dto ->
  val author: Sound.SourceAuthor? = if (dto.authorName != null && dto.authorUrl != null) {
    Sound.SourceAuthor(dto.authorName, dto.authorUrl)
  } else {
    null
  }

  Sound.Source(
    name = dto.name,
    url = dto.url,
    license = dto.license,
    author = author,
  )
}

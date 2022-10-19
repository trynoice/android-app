package com.github.ashutoshgngwr.noice.models

import com.github.ashutoshgngwr.noice.data.models.SoundInfoDto
import com.github.ashutoshgngwr.noice.data.models.SoundSourceDto
import java.io.Serializable

/**
 * @property id id of the sound.
 * @property group the [SoundGroup] that this [SoundInfo] belongs to.
 * @property name a user-presentable name of the sound.
 * @property iconSvg a user-presentable icon of the sound as a SVG string.
 * @property maxSilence The upper limit (in seconds) for the amount of silence to add in-between
 * segments for non-contiguous sounds.
 * @property isPremium whether the sound is only available to Premium users.
 * @property hasPremiumSegments whether the sound has any segments that are only available to
 * Premium users.
 * @property tags A list of tags that associate with this sound.
 * @property sources A list of details attributing original clip sources, author and license.
 */
data class SoundInfo(
  val id: String,
  val group: SoundGroup,
  val name: String,
  val iconSvg: String,
  val maxSilence: Int,
  val isPremium: Boolean,
  val hasPremiumSegments: Boolean,
  val tags: List<SoundTag>,
  val sources: List<SoundSource>,
) : Serializable {

  /**
   * Whether the sound is contiguous or contains silences in-between.
   */
  val isContiguous: Boolean
    get() = maxSilence == 0

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


data class SoundSource(
  val name: String,
  val url: String,
  val license: String,
  val author: SoundSourceAuthor? = null,
)

data class SoundSourceAuthor(
  val name: String,
  val url: String,
)


fun SoundInfoDto.toDomainEntity(): SoundInfo {
  return SoundInfo(
    id = metadata.id,
    group = group.toDomainEntity(),
    name = metadata.name,
    iconSvg = metadata.iconSvg,
    maxSilence = metadata.maxSilence,
    isPremium = metadata.isPremium,
    hasPremiumSegments = metadata.hasPremiumSegments,
    tags = tags.toDomainEntity(),
    sources = sources.toDomainEntity(),
  )
}

@JvmName("toDomainEntitySoundInfoDto")
fun List<SoundInfoDto>.toDomainEntity(): List<SoundInfo> {
  return map { it.toDomainEntity() }
}

@JvmName("toDomainEntitySoundSourceDto")
fun List<SoundSourceDto>.toDomainEntity(): List<SoundSource> = map { dto ->
  val author: SoundSourceAuthor? = if (dto.authorName != null && dto.authorUrl != null) {
    SoundSourceAuthor(dto.authorName, dto.authorUrl)
  } else {
    null
  }

  SoundSource(
    name = dto.name,
    url = dto.url,
    license = dto.license,
    author = author,
  )
}

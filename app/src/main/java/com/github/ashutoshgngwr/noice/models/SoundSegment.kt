package com.github.ashutoshgngwr.noice.models

import com.github.ashutoshgngwr.noice.data.models.SoundSegmentDto

data class SoundSegment(
  val name: String,
  val basePath: String,
  val isFree: Boolean,
  val isBridgeSegment: Boolean,
  val from: String? = null,
  val to: String? = null,
) {

  init {
    if (isBridgeSegment) {
      require(from != null) { "from must not be null for bridge segments" }
      require(to != null) { "to must not be null for bridge segments" }
    }
  }

  /**
   * Returns the full path of the segment relative to the `library-manifest.json` on the CDN server
   * for the given [bitrate].
   */
  fun path(bitrate: String): String {
    return "${basePath}/${bitrate}.mp3"
  }
}

fun List<SoundSegmentDto>.toDomainEntity(): List<SoundSegment> = map { dto ->
  SoundSegment(
    name = dto.name,
    isFree = dto.isFree,
    isBridgeSegment = dto.isBridgeSegment,
    basePath = dto.basePath,
    to = dto.to,
    from = dto.from,
  )
}

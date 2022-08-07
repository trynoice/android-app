package com.github.ashutoshgngwr.noice.model

import java.io.Serializable

/**
 * A thin wrapper around [API client's sound segment
 * model][com.trynoice.api.client.models.SoundSegment].
 *
 * @param name name of the segment.
 * @param isFree whether the segment is free.
 * @param isBridgeSegment whether this is bridge segment.
 * @param from if it [isBridgeSegment], then name of the segment that this segment bridges from.
 * @param to if it [isBridgeSegment], then name of the segment that this segment bridges to.
 * @param basePath path prefix of this segment relative to `library-manifest.json` on the CDN server
 * where individual bitrate files are located.
 */
data class SoundSegment(
  val name: String,
  val isFree: Boolean,
  val isBridgeSegment: Boolean,
  val from: String? = null,
  val to: String? = null,
  private val basePath: String,
) : Serializable {

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

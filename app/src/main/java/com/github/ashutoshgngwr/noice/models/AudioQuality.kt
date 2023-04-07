package com.github.ashutoshgngwr.noice.models

/**
 * Represents audio quality bit-rates supported by the CDN server.
 */
enum class AudioQuality(val bitrate: String) {
  LOW("128k"), MEDIUM("192k"), HIGH("256k"), ULTRA_HIGH("320k");

  companion object {
    /**
     * Returns an [AudioQuality] corresponding to the given [bitrate]. If the [bitrate] is `null`,
     * it returns the default audio bitrate.
     */
    fun fromBitrate(bitrate: String?): AudioQuality {
      return when (bitrate) {
        "192k" -> MEDIUM
        "256k" -> HIGH
        "320k" -> ULTRA_HIGH
        else -> LOW
      }
    }
  }
}

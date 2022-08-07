package com.github.ashutoshgngwr.noice.engine.exoplayer

import com.google.gson.annotations.Expose

/**
 * Additional metadata for ExoPlayer's [DownloadRequest
 * ][com.google.android.exoplayer2.offline.DownloadRequest].
 */
data class SoundDownloadMetadata(
  @Expose val md5sum: String,
  @Expose val soundId: String,
)

package com.github.ashutoshgngwr.noice.models

import com.google.gson.annotations.Expose

data class SoundDownloadMetadata(
  @Expose val md5sum: String,
  @Expose val soundId: String,
)

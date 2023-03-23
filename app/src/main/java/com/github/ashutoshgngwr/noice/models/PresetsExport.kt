package com.github.ashutoshgngwr.noice.models

data class PresetsExportV0(override val version: String, val data: String) : PresetsExport()

data class PresetsExportV1(
  override val version: String = VERSION_STRING,
  val presets: List<Preset>,
  val exportedAt: String,
) : PresetsExport() {

  companion object {
    const val VERSION_STRING = "v1"
  }
}

abstract class PresetsExport {
  abstract val version: String
}

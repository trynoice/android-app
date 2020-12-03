package com.github.ashutoshgngwr.noice

import com.github.ashutoshgngwr.noice.sound.Preset

// unnecessary unused warning: https://issuetracker.google.com/issues/74514347
@Suppress("unused")
class Application : android.app.Application() {

  override fun onCreate() {
    super.onCreate()
    Preset.migrateAllToV1(this)
  }
}

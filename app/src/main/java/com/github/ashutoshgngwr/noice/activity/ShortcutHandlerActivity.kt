package com.github.ashutoshgngwr.noice.activity

import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.repository.PresetRepository

class ShortcutHandlerActivity : AppCompatActivity() {

  companion object {
    const val EXTRA_PRESET_ID = "preset_name"
    const val EXTRA_SHORTCUT_ID = "shortcut_id"

    /**
     * Non-critical metadata; used for analytics.
     */
    const val EXTRA_SHORTCUT_TYPE = "shortcut_type"
  }

  private val analyticsProvider by lazy { NoiceApplication.of(this).analyticsProvider }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val params = bundleOf()
    intent.getStringExtra(EXTRA_PRESET_ID)?.also {
      // id is passed with the intent. For app version <= 0.15.0, preset id was treated as shortcut
      // id. Hence, using preset id as fallback in cases where shortcut id is null.
      reportShortcutUsage(intent.getStringExtra(EXTRA_SHORTCUT_ID) ?: it)

      if (PresetRepository.newInstance(this).get(it) == null) {
        Toast.makeText(this, R.string.preset_does_not_exist, Toast.LENGTH_LONG).show()
        params.putBoolean("success", false)
      } else {
        PlaybackController.playPreset(this, it)
        params.putBoolean("success", true)
      }
    }

    Intent(this, MainActivity::class.java)
      .putExtra(MainActivity.EXTRA_NAV_DESTINATION, R.id.presets)
      .also { startActivity(it) }

    finish()

    params.putString("shortcut_type", intent.getStringExtra(EXTRA_SHORTCUT_TYPE))
    analyticsProvider.logEvent("preset_shortcut_open", params)
  }

  private fun reportShortcutUsage(shortcutID: String) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N_MR1) {
      return
    }

    getSystemService(ShortcutManager::class.java).also {
      it.reportShortcutUsed(shortcutID)
    }
  }
}

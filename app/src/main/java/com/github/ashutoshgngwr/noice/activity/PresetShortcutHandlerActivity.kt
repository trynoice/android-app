package com.github.ashutoshgngwr.noice.activity

import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.metrics.AnalyticsProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.service.SoundPlaybackService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PresetShortcutHandlerActivity : AppCompatActivity() {

  companion object {
    const val EXTRA_PRESET_ID = "preset_name"
    const val EXTRA_SHORTCUT_ID = "shortcut_id"

    /**
     * Non-critical metadata; used for analytics.
     */
    const val EXTRA_SHORTCUT_TYPE = "shortcut_type"
  }

  @set:Inject
  internal var analyticsProvider: AnalyticsProvider? = null

  @set:Inject
  internal lateinit var presetRepository: PresetRepository

  @set:Inject
  internal lateinit var playbackServiceController: SoundPlaybackService.Controller

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val presetId = intent.getStringExtra(EXTRA_PRESET_ID)
    if (presetId == null) {
      finish()
      return
    }

    Intent(this, MainActivity::class.java)
      .putExtra(MainActivity.EXTRA_HOME_DESTINATION, R.id.presets)
      .also { startActivity(it) }

    // id is passed with the intent. For app version <= 0.15.0, preset id was treated as shortcut
    // id. Hence, using preset id as fallback in cases where shortcut id is null.
    reportShortcutUsage(intent.getStringExtra(EXTRA_SHORTCUT_ID) ?: presetId)
    lifecycleScope.launch {
      val params = bundleOf("shortcut_type" to intent.getStringExtra(EXTRA_SHORTCUT_TYPE))
      val preset = presetRepository.get(presetId)
      if (preset == null) {
        Toast.makeText(
          this@PresetShortcutHandlerActivity,
          R.string.preset_does_not_exist,
          Toast.LENGTH_LONG,
        ).show()

        params.putBoolean("success", false)
      } else {
        playbackServiceController.playPreset(preset)
        params.putBoolean("success", true)
      }

      finish()
      analyticsProvider?.logEvent("preset_shortcut_open", params)
    }
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

package com.github.ashutoshgngwr.noice

import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.ashutoshgngwr.noice.repository.PresetRepository

class ShortcutHandlerActivity : AppCompatActivity() {

  companion object {
    const val EXTRA_PRESET_ID = "preset_name"
    const val EXTRA_SHORTCUT_ID = "shortcut_id"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    intent.getStringExtra(EXTRA_PRESET_ID)?.also {
      // id is passed with the intent. For app version <= 0.15.0, preset id was treated as shortcut
      // id. Hence, using preset id as fallback in cases where shortcut id is null.
      reportShortcutUsage(intent.getStringExtra(EXTRA_SHORTCUT_ID) ?: it)

      if (PresetRepository.newInstance(this).get(it) == null) {
        Toast.makeText(this, R.string.preset_does_not_exist, Toast.LENGTH_LONG).show()
      } else {
        MediaPlayerService.playPreset(this, it)
      }
    }

    Intent(this, MainActivity::class.java)
      .putExtra(MainActivity.EXTRA_CURRENT_NAVIGATED_FRAGMENT, R.id.saved_presets)
      .also { startActivity(it) }

    finish()
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

package com.github.ashutoshgngwr.noice

import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.ashutoshgngwr.noice.sound.Preset

class ShortcutHandlerActivity : AppCompatActivity() {

  companion object {
    const val EXTRA_PRESET_ID = "preset_name"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    intent.getStringExtra(EXTRA_PRESET_ID)?.also {
      reportShortcutUsage(it)

      if (Preset.findByID(this, it) == null) {
        Toast.makeText(this, R.string.preset_does_not_exist, Toast.LENGTH_LONG).show()
      } else {
        Intent(this, MediaPlayerService::class.java)
          .setAction(MediaPlayerService.ACTION_PLAY_PRESET)
          .putExtra(MediaPlayerService.EXTRA_PRESET_ID, it)
          .also { intent -> startService(intent) }
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

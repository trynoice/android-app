package com.github.ashutoshgngwr.noice.activity

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.databinding.AlarmRingerActivityBinding
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.ncorti.slidetoact.SlideToActView

class AlarmRingerActivity : AppCompatActivity(), SlideToActView.OnSlideCompleteListener {

  companion object {
    @VisibleForTesting
    internal const val EXTRA_PRESET_ID = "preset_id"

    private const val RC_ALARM = 0x39

    fun getPendingIntent(context: Context, presetID: String?): PendingIntent {
      return Intent(context, AlarmRingerActivity::class.java)
        .putExtra(EXTRA_PRESET_ID, presetID)
        .let { PendingIntent.getActivity(context, RC_ALARM, it, PendingIntent.FLAG_UPDATE_CURRENT) }
    }
  }

  private lateinit var binding: AlarmRingerActivityBinding
  private lateinit var settingsRepository: SettingsRepository

  private var shouldPausePlaybackOnStop = false

  override fun onCreate(savedInstanceState: Bundle?) {
    settingsRepository = SettingsRepository.newInstance(this)
    AppCompatDelegate.setDefaultNightMode(settingsRepository.getAppThemeAsNightMode())
    super.onCreate(savedInstanceState)

    requestWindowFeature(Window.FEATURE_NO_TITLE);
    binding = AlarmRingerActivityBinding.inflate(layoutInflater)
    binding.dismissSlider.onSlideCompleteListener = this
    setContentView(binding.root)
    showWhenLocked()
    enableImmersiveMode()

    handleNewIntent()
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleNewIntent()
  }

  override fun onStop() {
    super.onStop()

    if (shouldPausePlaybackOnStop) {
      PlaybackController.pause(this)
      PlaybackController.setAudioUsage(this, AudioAttributesCompat.USAGE_MEDIA)
      shouldPausePlaybackOnStop = false
    }
  }

  override fun onBackPressed() = Unit

  override fun onSlideComplete(view: SlideToActView) {
    finish()
  }

  private fun enableImmersiveMode() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowCompat.getInsetsController(window, binding.root)?.apply {
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      hide(WindowInsetsCompat.Type.systemBars())
    }
  }

  private fun showWhenLocked() {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    } else {
      @Suppress("Deprecation")
      window.addFlags(
        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
          WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
      )
    }
  }


  private fun handleNewIntent() {
    val presetID = intent.getStringExtra(EXTRA_PRESET_ID)
    if (presetID == null) {
      finish()
      return
    }

    // Since activity takes a moment to actually show up, invoking `startService` from `onCreate` or
    // `onResume` fails with `java.lang.IllegalStateException: Not allowed to start service Intent:
    // app is in background` on recent Android version (O+). `PlaybackController` can not call
    // `startForegroundService` since it doesn't know if an action will bring `MediaPlayerService`
    // to foreground. To prevent `PlaybackController` from causing this error by invoking
    // `startService`, we need to manually start `MediaPlayerService` in the foreground since we can
    // be certain that playPreset action will bring it to foreground before Android System kills it.
    ContextCompat.startForegroundService(this, Intent(this, MediaPlayerService::class.java))

    PlaybackController.setAudioUsage(this, AudioAttributesCompat.USAGE_ALARM)
    PlaybackController.playPreset(this, presetID)
    shouldPausePlaybackOnStop = true
  }
}

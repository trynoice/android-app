package com.github.ashutoshgngwr.noice.activity

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Window
import android.view.WindowManager
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.databinding.AlarmRingerActivityBinding
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.playback.strategy.LocalPlaybackStrategy
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.ncorti.slidetoact.SlideToActView

class AlarmRingerActivity : AppCompatActivity(), SlideToActView.OnSlideCompleteListener {

  companion object {
    @VisibleForTesting
    internal const val EXTRA_PRESET_ID = "preset_id"

    private const val RC_ALARM = 0x39
    private val LOG_TAG = AlarmRingerActivity::class.simpleName

    fun getPendingIntent(context: Context, presetID: String?): PendingIntent {
      var piFlags = PendingIntent.FLAG_UPDATE_CURRENT
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        piFlags = piFlags or PendingIntent.FLAG_IMMUTABLE
      }

      return Intent(context, AlarmRingerActivity::class.java)
        .putExtra(EXTRA_PRESET_ID, presetID)
        .let { PendingIntent.getActivity(context, RC_ALARM, it, piFlags) }
    }
  }

  private lateinit var binding: AlarmRingerActivityBinding
  private lateinit var settingsRepository: SettingsRepository
  private lateinit var analyticsProvider: AnalyticsProvider
  private var ringerStartTime: Long = 0L

  override fun onCreate(savedInstanceState: Bundle?) {
    settingsRepository = SettingsRepository.newInstance(this)
    AppCompatDelegate.setDefaultNightMode(settingsRepository.getAppThemeAsNightMode())
    super.onCreate(savedInstanceState)

    supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
    binding = AlarmRingerActivityBinding.inflate(layoutInflater)
    binding.dismissSlider.onSlideCompleteListener = this
    setContentView(binding.root)
    showWhenLocked()

    analyticsProvider = NoiceApplication.of(this).analyticsProvider
    ringerStartTime = System.currentTimeMillis()
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    setIntent(intent)
  }

  override fun onResume() {
    super.onResume()
    enableImmersiveMode()

    // handle the new intent here since onResume() is guaranteed to be called after onNewIntent().
    // https://developer.android.com/reference/android/app/Activity#onNewIntent(android.content.Intent)
    val presetID = intent.getStringExtra(EXTRA_PRESET_ID)
    if (presetID == null) {
      Log.d(LOG_TAG, "onResume(): presetID is null")
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

    Log.d(LOG_TAG, "onResume(): starting preset")
    PlaybackController.setAudioUsage(this, AudioAttributesCompat.USAGE_ALARM)
    PlaybackController.playPreset(this, presetID)
  }

  override fun onStop() {
    super.onStop()
    if (!isFinishing) {
      return
    }

    Log.d(LOG_TAG, "onStop(): pausing playback")
    PlaybackController.pause(this)

    // TODO: find a better solution to wait for pause transition to finish before switching streams.
    Handler(mainLooper).postDelayed({
      PlaybackController.setAudioUsage(this, AudioAttributesCompat.USAGE_MEDIA)
    }, LocalPlaybackStrategy.DEFAULT_FADE_DURATION)

    val duration = System.currentTimeMillis() - ringerStartTime
    analyticsProvider.logEvent("alarm_ringer_session", bundleOf("duration_ms" to duration))
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
}

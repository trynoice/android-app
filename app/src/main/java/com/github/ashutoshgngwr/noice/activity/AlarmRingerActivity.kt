package com.github.ashutoshgngwr.noice.activity

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.databinding.AlarmRingerActivityBinding
import com.github.ashutoshgngwr.noice.engine.PlaybackController
import com.github.ashutoshgngwr.noice.engine.PlaybackService
import com.github.ashutoshgngwr.noice.engine.PlaybackState
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import com.ncorti.slidetoact.SlideToActView
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmRingerActivity : AppCompatActivity(), SlideToActView.OnSlideCompleteListener {

  private var ringerStartTime: Long = 0L
  private lateinit var binding: AlarmRingerActivityBinding

  @set:Inject
  internal lateinit var presetRepository: PresetRepository

  @set:Inject
  internal lateinit var soundRepository: SoundRepository

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  @set:Inject
  internal lateinit var playbackController: PlaybackController

  private val settingsRepository by lazy {
    EntryPointAccessors.fromApplication(application, AlarmRingerActivityEntryPoint::class.java)
      .settingsRepository()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    AppCompatDelegate.setDefaultNightMode(settingsRepository.getAppThemeAsNightMode())
    super.onCreate(savedInstanceState)

    supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
    binding = AlarmRingerActivityBinding.inflate(layoutInflater)
    binding.dismissSlider.onSlideCompleteListener = this
    setContentView(binding.root)
    showWhenLocked()
    ringerStartTime = System.currentTimeMillis()

    lifecycleScope.launch {
      soundRepository.getPlayerManagerState()
        .first { it == PlaybackState.PAUSED }

      playbackController.setAudioUsage(AudioAttributesCompat.USAGE_MEDIA)
      val duration = System.currentTimeMillis() - ringerStartTime
      analyticsProvider.logEvent("alarm_ringer_session", bundleOf("duration_ms" to duration))
      finish()
    }
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
    val preset = presetRepository.get(intent.getStringExtra(EXTRA_PRESET_ID))
    if (preset == null) {
      Log.w(LOG_TAG, "onResume(): preset is null")
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
    ContextCompat.startForegroundService(this, Intent(this, PlaybackService::class.java))

    Log.d(LOG_TAG, "onResume(): starting preset")
    binding.dismissSlider.isVisible = true
    binding.dismissProgress.isVisible = false
    playbackController.setAudioUsage(AudioAttributesCompat.USAGE_ALARM)
    playbackController.play(preset)
  }

  override fun onBackPressed() = Unit

  override fun onSlideComplete(view: SlideToActView) {
    Log.d(LOG_TAG, "onStop(): pausing playback")
    binding.dismissSlider.isVisible = false
    binding.dismissProgress.isVisible = true
    playbackController.pause()
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

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface AlarmRingerActivityEntryPoint {
    fun settingsRepository(): SettingsRepository
  }

  companion object {
    @VisibleForTesting
    internal const val EXTRA_PRESET_ID = "preset_id"

    private const val RC_ALARM = 0x39
    private val LOG_TAG = AlarmRingerActivity::class.simpleName

    /**
     * Returns a [PendingIntent] that starts the [AlarmRingerActivity] with the given [presetID].
     */
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
}

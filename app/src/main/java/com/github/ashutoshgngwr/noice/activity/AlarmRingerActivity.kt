package com.github.ashutoshgngwr.noice.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import com.github.ashutoshgngwr.noice.databinding.AlarmRingerActivityBinding
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@AndroidEntryPoint
class AlarmRingerActivity : AppCompatActivity() {

  private lateinit var binding: AlarmRingerActivityBinding

  @set:Inject
  internal lateinit var serviceController: ServiceController

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  private val settingsRepository by lazy {
    EntryPointAccessors.fromApplication(application, AlarmRingerActivityEntryPoint::class.java)
      .settingsRepository()
  }

  private val dismissBroadcastReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      finish()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    AppCompatDelegate.setDefaultNightMode(settingsRepository.getAppThemeAsNightMode())
    super.onCreate(savedInstanceState)

    supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
    binding = AlarmRingerActivityBinding.inflate(layoutInflater)

    binding.dismiss.setOnClickListener {
      serviceController.dismiss(intent.getIntExtra(EXTRA_ALARM_ID, -1))
    }

    binding.snooze.setOnClickListener {
      serviceController.snooze(intent.getIntExtra(EXTRA_ALARM_ID, -1))
    }

    setContentView(binding.root)
    showWhenLocked()
    onBackPressedDispatcher.addCallback(this) { } // no-op
  }

  override fun onStart() {
    super.onStart()
    registerReceiver(dismissBroadcastReceiver, IntentFilter(ACTION_DISMISS))
  }

  override fun onStop() {
    unregisterReceiver(dismissBroadcastReceiver)
    super.onStop()
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    if (intent != null) setIntent(intent)
  }

  override fun onResume() {
    super.onResume()
    enableImmersiveMode()
    binding.triggerTime.text = intent?.getStringExtra(EXTRA_ALARM_TRIGGER_TIME)
    binding.label.text = intent?.getStringExtra(EXTRA_ALARM_LABEL)
    binding.label.isVisible = !binding.label.text.isNullOrBlank()
  }

  private fun enableImmersiveMode() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowCompat.getInsetsController(window, binding.root).apply {
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

  companion object {
    private const val ACTION_DISMISS = "dismiss"
    private const val EXTRA_ALARM_ID = "alarmId"
    private const val EXTRA_ALARM_LABEL = "alarmLabel"
    private const val EXTRA_ALARM_TRIGGER_TIME = "alarmTriggerTime"

    fun buildIntent(
      context: Context,
      alarmId: Int,
      alarmLabel: String?,
      alarmTriggerTime: String,
    ): Intent {
      return Intent(context, AlarmRingerActivity::class.java)
        .putExtra(EXTRA_ALARM_ID, alarmId)
        .putExtra(EXTRA_ALARM_LABEL, alarmLabel)
        .putExtra(EXTRA_ALARM_TRIGGER_TIME, alarmTriggerTime)
    }

    fun dismiss(context: Context) {
      context.sendBroadcast(Intent(ACTION_DISMISS))
    }
  }

  interface ServiceController {
    fun dismiss(alarmId: Int)
    fun snooze(alarmId: Int)
  }

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface AlarmRingerActivityEntryPoint {
    fun settingsRepository(): SettingsRepository
  }
}

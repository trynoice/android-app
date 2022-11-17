package com.github.ashutoshgngwr.noice.activity

import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.fragment.AlarmsFragmentArgs
import com.github.ashutoshgngwr.noice.models.Alarm
import com.github.ashutoshgngwr.noice.repository.AlarmRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SetAlarmHandlerActivity : AppCompatActivity() {

  @set:Inject
  internal lateinit var alarmRepository: AlarmRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (intent?.action != AlarmClock.ACTION_SET_ALARM) {
      finish()
      return
    }

    lifecycleScope.launch {
      val alarm = buildAlarmFromIntent()
      if (alarm == null) {
        startAlarmsFragment(null)
      } else {
        val savedId = alarmRepository.save(alarm)
        if (!intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)) {
          startAlarmsFragment(savedId)
        }
      }

      finish()
    }
  }

  private fun startAlarmsFragment(alarmId: Int?) {
    Intent(this, MainActivity::class.java)
      .putExtra(MainActivity.EXTRA_HOME_DESTINATION, R.id.alarms)
      .apply {
        if (alarmId != null) putExtra(
          MainActivity.EXTRA_HOME_DESTINATION_ARGS,
          AlarmsFragmentArgs(alarmId).toBundle(),
        )
      }
      .also { startActivity(it) }
  }

  private fun buildAlarmFromIntent(): Alarm? {
    if (!intent.hasExtra(AlarmClock.EXTRA_HOUR) || !intent.hasExtra(AlarmClock.EXTRA_MINUTES)) {
      return null
    }

    val hours = intent.getIntExtra(AlarmClock.EXTRA_HOUR, 0)
    val minutes = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0)
    val weeklySchedule = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS).let { days ->
      var schedule = 0
      days?.forEach { schedule = schedule or (1 shl (it - 1)) }
      schedule
    }

    return Alarm(
      id = 0,
      label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE),
      isEnabled = true,
      minuteOfDay = hours * 60 + minutes,
      weeklySchedule = weeklySchedule,
      preset = null,
      vibrate = intent.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, true),
    )
  }
}

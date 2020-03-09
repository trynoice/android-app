package com.github.ashutoshgngwr.noice.fragment

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.R
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_sleep_timer.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.math.floor

class SleepTimerFragment : Fragment() {

  companion object {
    private const val KEY_HOUR = "hour"
    private const val KEY_MINUTE = "minute"
  }

  data class ScheduleAutoSleepEvent(val atUptimeMillis: Long)

  private val eventBus = EventBus.getDefault()
  private val timerExpiredCallback = Runnable {
    onScheduleAutoSleep(ScheduleAutoSleepEvent(0))
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.fragment_sleep_timer, container, false)
      .also {
        it.time_picker.setIs24HourView(true)
      }
  }

  @Suppress("DEPRECATION")
  override fun onSaveInstanceState(outState: Bundle) {
    val timePicker = requireView().time_picker
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      outState.putInt(KEY_HOUR, timePicker.hour)
      outState.putInt(KEY_MINUTE, timePicker.minute)
    } else {
      outState.putInt(KEY_HOUR, timePicker.currentHour)
      outState.putInt(KEY_MINUTE, timePicker.currentMinute)
    }
  }

  @Suppress("DEPRECATION")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val timePicker = view.time_picker
    var hour = 0
    var minute = 0
    if (savedInstanceState != null) {
      hour = savedInstanceState.getInt(KEY_HOUR, 0)
      minute = savedInstanceState.getInt(KEY_MINUTE, 0)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      timePicker.hour = hour
      timePicker.minute = minute
    } else {
      timePicker.currentHour = hour
      timePicker.currentMinute = minute
    }

    view.button_schedule.setOnClickListener {
      val hours: Int
      val minutes: Int

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        hours = timePicker.hour
        minutes = timePicker.minute
      } else {
        hours = timePicker.currentHour
        minutes = timePicker.currentMinute
      }

      if (hours == 0 && minutes == 0) {
        Snackbar.make(requireView(), R.string.auto_sleep_schedule_error, Snackbar.LENGTH_SHORT)
          .show()
        return@setOnClickListener
      }

      val millis = SystemClock.uptimeMillis() + hours * 3600000 + minutes * 60000
      eventBus.postSticky(ScheduleAutoSleepEvent(millis))
      Snackbar.make(requireView(), R.string.auto_sleep_schedule_success, Snackbar.LENGTH_SHORT)
        .show()
    }

    view.button_reset.setOnClickListener {
      eventBus.postSticky(ScheduleAutoSleepEvent(0))
      Snackbar.make(requireView(), R.string.auto_sleep_schedule_cancelled, Snackbar.LENGTH_SHORT)
        .show()
    }

    eventBus.register(this)
  }

  @Suppress("DEPRECATION")
  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onScheduleAutoSleep(event: ScheduleAutoSleepEvent) {
    val remainingMillis = event.atUptimeMillis - SystemClock.uptimeMillis()
    var hour = 0
    var minute = 0
    if (remainingMillis > 0) {
      hour = (remainingMillis / 3600000).toInt()
      minute = floor((remainingMillis % 3600000).toFloat() / 60000).toInt()

      // schedule a callback to refresh views when timer expires
      requireView().postDelayed(timerExpiredCallback, remainingMillis)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      requireView().time_picker.hour = hour
      requireView().time_picker.minute = minute
    } else {
      requireView().time_picker.currentHour = hour
      requireView().time_picker.currentMinute = minute
    }

    requireView().button_reset.isEnabled = remainingMillis > 0
  }

  override fun onDestroyView() {
    requireView().removeCallbacks(timerExpiredCallback)
    eventBus.unregister(this)
    super.onDestroyView()
  }
}

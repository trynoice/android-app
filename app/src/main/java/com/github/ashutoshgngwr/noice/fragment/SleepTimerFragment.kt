package com.github.ashutoshgngwr.noice.fragment

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

class SleepTimerFragment : Fragment() {

  data class ScheduleAutoSleepEvent(val atUptimeMillis: Long)

  private var eventBus = EventBus.getDefault()

  private val addToSleepDurationButtonClickListener = View.OnClickListener {
    val timeToAdd = 1000L * 60L * when (it.id) {
      R.id.button_1m -> 1
      R.id.button_5m -> 5
      R.id.button_30m -> 30
      R.id.button_1h -> 60
      R.id.button_4h -> 240
      R.id.button_8h -> 480
      else -> 0
    }

    var currentDeadline = SystemClock.uptimeMillis()
    val lastEvent = eventBus.getStickyEvent(ScheduleAutoSleepEvent::class.java)
    if (lastEvent != null && lastEvent.atUptimeMillis > currentDeadline) {
      currentDeadline = lastEvent.atUptimeMillis
    }

    eventBus.postSticky(ScheduleAutoSleepEvent(currentDeadline + timeToAdd))
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.fragment_sleep_timer, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    view.button_reset.setOnClickListener {
      eventBus.postSticky(ScheduleAutoSleepEvent(0))
      Snackbar.make(requireView(), R.string.auto_sleep_schedule_cancelled, Snackbar.LENGTH_SHORT)
        .show()
    }

    view.button_1m.setOnClickListener(addToSleepDurationButtonClickListener)
    view.button_5m.setOnClickListener(addToSleepDurationButtonClickListener)
    view.button_30m.setOnClickListener(addToSleepDurationButtonClickListener)
    view.button_1h.setOnClickListener(addToSleepDurationButtonClickListener)
    view.button_4h.setOnClickListener(addToSleepDurationButtonClickListener)
    view.button_8h.setOnClickListener(addToSleepDurationButtonClickListener)

    eventBus.register(this)
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onScheduleAutoSleep(event: ScheduleAutoSleepEvent) {
    val remainingMillis = event.atUptimeMillis - SystemClock.uptimeMillis()
    requireView().button_reset.isEnabled = remainingMillis > 0
    requireView().countdown_view.startCountdown(remainingMillis)
  }

  override fun onDestroyView() {
    eventBus.unregister(this)
    super.onDestroyView()
  }
}

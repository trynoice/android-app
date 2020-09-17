package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.InAppReviewFlowManager
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_sleep_timer.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class SleepTimerFragment : Fragment(R.layout.fragment_sleep_timer) {

  private val eventBus = EventBus.getDefault()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    view.duration_picker.setResetButtonEnabled(false)
    view.duration_picker.setOnDurationAddedListener(this::onDurationAdded)
    eventBus.register(this)
  }

  private fun onDurationAdded(duration: Long) {
    if (duration < 0) { // duration picker reset
      eventBus.postSticky(MediaPlayerService.ScheduleAutoSleepEvent(0))
      Snackbar.make(requireView(), R.string.auto_sleep_schedule_cancelled, Snackbar.LENGTH_SHORT)
        .show()

      return
    }

    var currentDeadline = SystemClock.uptimeMillis()
    val lastEvent = eventBus.getStickyEvent(MediaPlayerService.ScheduleAutoSleepEvent::class.java)
    if (lastEvent != null && lastEvent.atUptimeMillis > currentDeadline) {
      currentDeadline = lastEvent.atUptimeMillis
    }

    eventBus.postSticky(MediaPlayerService.ScheduleAutoSleepEvent(currentDeadline + duration))
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onScheduleAutoSleep(event: MediaPlayerService.ScheduleAutoSleepEvent) {
    val remainingMillis = event.atUptimeMillis - SystemClock.uptimeMillis()
    requireView().duration_picker.setResetButtonEnabled(remainingMillis > 0)
    requireView().countdown_view.startCountdown(remainingMillis)

    // maybe show in-app review dialog to the user
    InAppReviewFlowManager.maybeAskForReview(requireActivity())
  }

  override fun onDestroyView() {
    eventBus.unregister(this)
    super.onDestroyView()
  }
}

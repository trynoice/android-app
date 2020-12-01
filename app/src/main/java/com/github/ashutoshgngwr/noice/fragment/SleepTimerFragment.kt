package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.InAppReviewFlowManager
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SleepTimerFragmentBinding
import com.google.android.material.snackbar.Snackbar
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class SleepTimerFragment : Fragment() {

  private lateinit var binding: SleepTimerFragmentBinding
  private val eventBus = EventBus.getDefault()

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = SleepTimerFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.durationPicker.setResetButtonEnabled(false)
    binding.durationPicker.setOnDurationAddedListener(this::onDurationAdded)
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
    binding.durationPicker.setResetButtonEnabled(remainingMillis > 0)
    binding.countdownView.startCountdown(remainingMillis)

    // maybe show in-app review dialog to the user
    InAppReviewFlowManager.maybeAskForReview(requireActivity())
  }

  override fun onDestroyView() {
    eventBus.unregister(this)
    super.onDestroyView()
  }
}

package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SleepTimerFragmentBinding
import com.github.ashutoshgngwr.noice.engine.PlaybackController
import com.github.ashutoshgngwr.noice.ext.showInfoSnackBar
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SleepTimerFragment : Fragment() {

  private lateinit var binding: SleepTimerFragmentBinding

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  @set:Inject
  internal lateinit var reviewFlowProvider: ReviewFlowProvider

  @set:Inject
  internal lateinit var playbackController: PlaybackController

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = SleepTimerFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.durationPicker.setResetButtonEnabled(false)
    binding.durationPicker.setOnDurationAddedListener(this::onDurationAdded)

    val duration = playbackController.getStopScheduleRemainingMillis()
    if (duration > 0) {
      binding.countdownView.startCountdown(duration)
      binding.durationPicker.setResetButtonEnabled(true)
    }

    analyticsProvider.setCurrentScreen("sleep_timer", SleepTimerFragment::class)
  }

  override fun onDestroyView() {
    val duration = playbackController.getStopScheduleRemainingMillis()
    if (duration > 0) {
      analyticsProvider.logEvent("sleep_timer_set", bundleOf("duration_ms" to duration))
    }

    super.onDestroyView()
  }

  private fun onDurationAdded(duration: Long) {
    var remaining = 0L
    var enableResetButton = false
    if (duration < 0) { // duration picker reset
      playbackController.clearScheduledAutoStop()
      analyticsProvider.logEvent("sleep_timer_cancel", bundleOf())
      showInfoSnackBar(R.string.auto_sleep_schedule_cancelled, snackBarAnchorView())
    } else {
      remaining = playbackController.getStopScheduleRemainingMillis()
      remaining += duration
      playbackController.scheduleStop(remaining)
      enableResetButton = true
      analyticsProvider.logEvent("sleep_timer_add_duration", bundleOf("duration_ms" to duration))
    }

    binding.durationPicker.setResetButtonEnabled(enableResetButton)
    binding.countdownView.startCountdown(remaining)
    // maybe show in-app review dialog to the user
    reviewFlowProvider.maybeAskForReview(requireActivity())
  }

  private fun snackBarAnchorView(): View? {
    return activity?.findViewById<View?>(R.id.playback_controller)
      ?.takeIf { it.isVisible }
  }
}

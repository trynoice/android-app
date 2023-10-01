package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SleepTimerFragmentBinding
import com.github.ashutoshgngwr.noice.ext.showInfoSnackBar
import com.github.ashutoshgngwr.noice.metrics.AnalyticsProvider
import com.github.ashutoshgngwr.noice.metrics.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.service.SoundPlaybackService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SleepTimerFragment : Fragment() {

  @set:Inject
  internal var analyticsProvider: AnalyticsProvider? = null

  @set:Inject
  internal lateinit var reviewFlowProvider: ReviewFlowProvider

  @set:Inject
  internal lateinit var playbackServiceController: SoundPlaybackService.Controller

  private lateinit var binding: SleepTimerFragmentBinding

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = SleepTimerFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.durationPicker.setResetButtonEnabled(false)
    binding.durationPicker.setOnDurationAddedListener(this::onDurationAdded)

    val duration = playbackServiceController.getStopScheduleRemainingMillis()
    if (duration > 0) {
      binding.countdownView.startCountdown(duration)
      binding.durationPicker.setResetButtonEnabled(true)
    }

    analyticsProvider?.setCurrentScreen(this::class)
  }

  private fun onDurationAdded(duration: Long) {
    var remaining = 0L
    var enableResetButton = false
    if (duration < 0) { // duration picker reset
      playbackServiceController.clearStopSchedule()
      showInfoSnackBar(R.string.auto_sleep_schedule_cancelled, snackBarAnchorView())
    } else {
      remaining = playbackServiceController.getStopScheduleRemainingMillis()
      remaining += duration
      playbackServiceController.scheduleStop(remaining)
      enableResetButton = true
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

package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.InAppReviewFlowManager
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SleepTimerFragmentBinding
import com.google.android.material.snackbar.Snackbar

class SleepTimerFragment : Fragment() {

  private lateinit var binding: SleepTimerFragmentBinding

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

    MediaPlayerService.getScheduledStopPlaybackRemainingDurationMillis().also {
      if (it > 0) {
        binding.countdownView.startCountdown(it)
      }
    }
  }

  private fun onDurationAdded(duration: Long) {
    var remainingMillis = 0L
    var enableResetButton = false
    if (duration < 0) { // duration picker reset
      MediaPlayerService.scheduleStopPlayback(requireContext(), 0)
      Snackbar.make(requireView(), R.string.auto_sleep_schedule_cancelled, Snackbar.LENGTH_SHORT)
        .show()
    } else {
      remainingMillis = MediaPlayerService.getScheduledStopPlaybackRemainingDurationMillis()
      remainingMillis += duration
      MediaPlayerService.scheduleStopPlayback(requireContext(), remainingMillis)
      enableResetButton = true
    }

    binding.durationPicker.setResetButtonEnabled(enableResetButton)
    binding.countdownView.startCountdown(remainingMillis)
    // maybe show in-app review dialog to the user
    InAppReviewFlowManager.maybeAskForReview(requireActivity())
  }
}

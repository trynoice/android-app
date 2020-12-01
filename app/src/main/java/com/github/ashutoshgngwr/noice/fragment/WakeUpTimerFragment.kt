package com.github.ashutoshgngwr.noice.fragment

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.InAppReviewFlowManager
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.WakeUpTimerManager
import com.github.ashutoshgngwr.noice.databinding.WakeUpTimerFragmentBinding
import com.github.ashutoshgngwr.noice.sound.Preset
import java.util.*

class WakeUpTimerFragment : Fragment() {

  private lateinit var binding: WakeUpTimerFragmentBinding
  private var selectedPresetName: String? = null
  private var selectedTime: Long = 0

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = WakeUpTimerFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.selectPresetButton.setOnClickListener { onSelectPresetClicked() }
    binding.resetTimeButton.setOnClickListener { onResetTimeClicked() }
    binding.setTimeButton.setOnClickListener { onSetTimeClicked() }

    val timer = WakeUpTimerManager.get(requireContext())
    if (timer?.atMillis ?: 0 > System.currentTimeMillis()) {
      selectedPresetName = timer?.presetName
      selectedTime = timer?.atMillis ?: 0
    }

    // check selectedPresetName exists in user preferences.
    selectedPresetName?.also {
      if (Preset.findByName(requireContext(), it) == null) {
        selectedPresetName = null
        selectedTime = 0
      }
    }

    notifyUpdate()
  }

  private fun onSelectPresetClicked() {
    DialogFragment.show(childFragmentManager) {
      val presets = Preset.readAllFromUserPreferences(requireContext()).map { it.name }
      title(R.string.select_preset)
      if (presets.isNotEmpty()) {
        singleChoiceItems(presets.toTypedArray(), presets.indexOf(selectedPresetName)) { choice ->
          selectedPresetName = presets[choice]
          notifyUpdate()
        }
      } else {
        message(R.string.preset_info__description)
        positiveButton(android.R.string.ok)
      }
    }
  }

  private fun onResetTimeClicked() {
    selectedTime = 0
    selectedPresetName = null
    notifyUpdate()
  }

  private fun onSetTimeClicked() {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.SECOND, 0)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      calendar.set(Calendar.HOUR_OF_DAY, binding.timePicker.hour)
      calendar.set(Calendar.MINUTE, binding.timePicker.minute)
    } else {
      @Suppress("DEPRECATION")
      calendar.set(Calendar.HOUR_OF_DAY, binding.timePicker.currentHour)

      @Suppress("DEPRECATION")
      calendar.set(Calendar.MINUTE, binding.timePicker.currentMinute)
    }

    if (calendar.timeInMillis < System.currentTimeMillis()) {
      // user has selected a time that is in the past. In this scenario the alarm should be
      // scheduled on the next day.
      calendar.add(Calendar.DAY_OF_MONTH, 1)
    }

    selectedTime = calendar.timeInMillis
    notifyUpdate()

    // maybe show in-app review dialog to the user
    InAppReviewFlowManager.maybeAskForReview(requireActivity())
  }

  /**
   * [notifyUpdate] updates user interface according to currently set timer values. It also
   * schedules or cancels timer using [WakeUpTimerManager] based on these values.
   */
  private fun notifyUpdate() {
    val isTimerValid = selectedTime - System.currentTimeMillis() > 0 && selectedPresetName != null
    binding.setTimeButton.isEnabled = selectedPresetName != null
    binding.resetTimeButton.isEnabled = isTimerValid

    updateTimePicker()
    if (selectedPresetName == null) {
      binding.selectPresetButton.setText(R.string.select_preset)
    } else {
      binding.selectPresetButton.text = selectedPresetName
    }

    if (isTimerValid) {
      WakeUpTimerManager.set(
        requireContext(),
        WakeUpTimerManager.Timer(requireNotNull(selectedPresetName), selectedTime)
      )
    } else {
      WakeUpTimerManager.cancel(requireContext())
    }
  }

  private fun updateTimePicker() {
    val calendar = Calendar.getInstance()
    if (selectedTime > System.currentTimeMillis()) {
      calendar.timeInMillis = selectedTime
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      binding.timePicker.hour = calendar.get(Calendar.HOUR_OF_DAY)
      binding.timePicker.minute = calendar.get(Calendar.MINUTE)
    } else {
      @Suppress("DEPRECATION")
      binding.timePicker.currentHour = calendar.get(Calendar.HOUR_OF_DAY)

      @Suppress("DEPRECATION")
      binding.timePicker.currentMinute = calendar.get(Calendar.MINUTE)
    }
  }
}

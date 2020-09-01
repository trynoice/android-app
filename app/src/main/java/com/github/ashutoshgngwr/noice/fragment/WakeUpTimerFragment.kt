package com.github.ashutoshgngwr.noice.fragment

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.WakeUpTimerManager
import com.github.ashutoshgngwr.noice.sound.Preset
import kotlinx.android.synthetic.main.fragment_wake_up_timer.*
import java.util.*

class WakeUpTimerFragment : Fragment(R.layout.fragment_wake_up_timer) {

  private var selectedPresetName: String? = null
  private var selectedTime: Long = 0

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    button_select_preset.setOnClickListener { onSelectPresetClicked() }
    button_reset_time.setOnClickListener { onResetTimeClicked() }
    button_set_time.setOnClickListener { onSetTimeClicked() }

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
    DialogFragment().show(childFragmentManager) {
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
      calendar.set(Calendar.HOUR_OF_DAY, time_picker.hour)
      calendar.set(Calendar.MINUTE, time_picker.minute)
    } else {
      @Suppress("DEPRECATION")
      calendar.set(Calendar.HOUR_OF_DAY, time_picker.currentHour)

      @Suppress("DEPRECATION")
      calendar.set(Calendar.MINUTE, time_picker.currentMinute)
    }

    if (calendar.timeInMillis < System.currentTimeMillis()) {
      // user has selected a time that is in the past. In this scenario the alarm should be
      // scheduled on the next day.
      calendar.add(Calendar.DAY_OF_MONTH, 1)
    }

    selectedTime = calendar.timeInMillis
    notifyUpdate()
  }

  /**
   * [notifyUpdate] updates user interface according to currently set timer values. It also
   * schedules or cancels timer using [WakeUpTimerManager] based on these values.
   */
  private fun notifyUpdate() {
    val isTimerValid = selectedTime - System.currentTimeMillis() > 0 && selectedPresetName != null
    button_set_time.isEnabled = selectedPresetName != null
    button_reset_time.isEnabled = isTimerValid

    updateTimePicker()
    if (selectedPresetName == null) {
      button_select_preset.setText(R.string.select_preset)
    } else {
      button_select_preset.text = selectedPresetName
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
      time_picker.hour = calendar.get(Calendar.HOUR_OF_DAY)
      time_picker.minute = calendar.get(Calendar.MINUTE)
    } else {
      @Suppress("DEPRECATION")
      time_picker.currentHour = calendar.get(Calendar.HOUR_OF_DAY)

      @Suppress("DEPRECATION")
      time_picker.currentMinute = calendar.get(Calendar.MINUTE)
    }
  }
}

package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.WakeUpTimerManager
import com.github.ashutoshgngwr.noice.sound.Preset
import kotlinx.android.synthetic.main.fragment_sleep_timer.countdown_view
import kotlinx.android.synthetic.main.fragment_wake_up_timer.*
import kotlinx.android.synthetic.main.fragment_wake_up_timer.view.*

class WakeUpTimerFragment : Fragment(R.layout.fragment_wake_up_timer) {

  private var selectedPresetName: String? = null
  private var selectedTime: Long = 0

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    button_select_preset.setOnClickListener { onSelectPresetClicked() }
    duration_picker.setOnDurationAddedListener(this::onDurationAdded)

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
      singleChoiceItems(presets.toTypedArray(), presets.indexOf(selectedPresetName)) { choice ->
        selectedPresetName = presets[choice]
        notifyUpdate()
      }
    }
  }

  private fun onDurationAdded(duration: Long) {
    if (duration < 0) {
      selectedTime = 0
      notifyUpdate()
      return
    }

    if (selectedTime < System.currentTimeMillis()) {
      selectedTime = System.currentTimeMillis() + duration
    } else {
      selectedTime += duration
    }

    notifyUpdate()
  }

  /**
   * [notifyUpdate] updates user interface according to currently set timer values. It also
   * schedules or cancels timer using [WakeUpTimerManager] based on these values.
   */
  private fun notifyUpdate() {
    val remainingDuration = selectedTime - System.currentTimeMillis()
    countdown_view.startCountdown(remainingDuration)
    requireView().duration_picker.also {
      it.setControlsEnabled(selectedPresetName != null)
      it.setResetButtonEnabled(remainingDuration > 0)
    }

    if (selectedPresetName == null) {
      button_select_preset.setText(R.string.select_preset)
    } else {
      button_select_preset.text = selectedPresetName
    }

    if (remainingDuration > 0 && selectedPresetName != null) {
      WakeUpTimerManager.set(
        requireContext(),
        WakeUpTimerManager.Timer(requireNotNull(selectedPresetName), selectedTime)
      )
    } else {
      WakeUpTimerManager.cancel(requireContext())
    }
  }
}

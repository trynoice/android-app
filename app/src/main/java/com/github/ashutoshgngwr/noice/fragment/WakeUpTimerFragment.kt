package com.github.ashutoshgngwr.noice.fragment

import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.media.AudioManagerCompat
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.WakeUpTimerManager
import com.github.ashutoshgngwr.noice.databinding.WakeUpTimerFragmentBinding
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.google.android.material.snackbar.Snackbar
import java.util.*
import java.util.concurrent.TimeUnit

class WakeUpTimerFragment : Fragment() {

  private lateinit var audioManager: AudioManager
  private lateinit var binding: WakeUpTimerFragmentBinding
  private lateinit var presetRepository: PresetRepository
  private lateinit var analyticsProvider: AnalyticsProvider

  private var selectedPresetID: String? = null
  private var selectedTime: Long = 0
  private var changedPreset = false

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
    binding.is24hView.setOnCheckedChangeListener { _, enabled ->
      binding.timePicker.setIs24HourView(enabled)
    }

    binding.shouldUpdateMediaVolume.setOnCheckedChangeListener { _, enabled ->
      binding.mediaVolumeSlider.isEnabled = enabled
    }

    audioManager = requireNotNull(requireContext().getSystemService())
    presetRepository = PresetRepository.newInstance(requireContext())
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val min = AudioManagerCompat.getStreamMinVolume(audioManager, AudioManager.STREAM_MUSIC)

    binding.mediaVolumeSlider.isEnabled = binding.shouldUpdateMediaVolume.isChecked
    binding.mediaVolumeSlider.valueFrom = min.toFloat()
    binding.mediaVolumeSlider.valueTo = max.toFloat()
    binding.mediaVolumeSlider.stepSize = 1f
    binding.mediaVolumeSlider.setLabelFormatter { "${(it * 100).toInt() / max}%" }

    WakeUpTimerManager.get(requireContext())?.also {
      if (it.atMillis > System.currentTimeMillis()) {
        selectedPresetID = it.presetID
        selectedTime = it.atMillis
        binding.shouldUpdateMediaVolume.isChecked = it.shouldUpdateMediaVolume
        binding.mediaVolumeSlider.value = it.mediaVolume.toFloat()
      }
    }

    // check selectedPresetID exists in user preferences.
    if (presetRepository.get(selectedPresetID) == null) {
      resetControls()
    }

    notifyUpdate()

    analyticsProvider = NoiceApplication.of(requireContext()).getAnalyticsProvider()
    analyticsProvider.setCurrentScreen("wake_up_timer", WakeUpTimerFragment::class)
  }

  private fun onSelectPresetClicked() {
    DialogFragment.show(childFragmentManager) {
      val presets = presetRepository.list()
      val presetNames = presets.map { it.name }.toTypedArray()
      val presetIDs = presets.map { it.id }
      title(R.string.select_preset)
      if (presets.isNotEmpty()) {
        singleChoiceItems(presetNames, presetIDs.indexOf(selectedPresetID)) { choice ->
          selectedPresetID = presetIDs[choice]
          changedPreset = true
          notifyUpdate()
          WakeUpTimerManager.saveLastUsedPresetID(requireContext(), selectedPresetID)
        }
        negativeButton(R.string.cancel)
      } else {
        message(R.string.preset_info__description)
        positiveButton(android.R.string.ok)
      }
    }
  }

  private fun onResetTimeClicked() {
    resetControls()
    notifyUpdate()
    Snackbar.make(requireView(), R.string.wake_up_timer_cancelled, Snackbar.LENGTH_LONG).show()
    analyticsProvider.logEvent("wake_up_timer_cancel", bundleOf())
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
    notifyScheduleLeftTime()

    val params = bundleOf(
      "hour" to calendar.get(Calendar.HOUR_OF_DAY),
      "minute" to calendar.get(Calendar.MINUTE),
      "duration_ms" to calendar.timeInMillis - System.currentTimeMillis()
    )

    analyticsProvider.logEvent("wake_up_timer_set", params)
    // maybe show in-app review dialog to the user
    NoiceApplication.of(requireContext())
      .getReviewFlowProvider()
      .maybeAskForReview(requireActivity())
  }

  /**
   * [notifyUpdate] updates user interface according to currently set timer values. It also
   * schedules or cancels timer using [WakeUpTimerManager] based on these values.
   */
  private fun notifyUpdate() {
    val selectedPreset = presetRepository.get(selectedPresetID)
    val isTimerValid = selectedTime > System.currentTimeMillis() && selectedPreset != null
    binding.setTimeButton.isEnabled = selectedPreset != null
    binding.resetTimeButton.isEnabled = isTimerValid

    updateTimePicker()
    if (selectedPreset == null) {
      binding.selectPresetButton.setText(R.string.select_preset)
    } else {
      binding.selectPresetButton.text = selectedPreset.name
    }

    if (isTimerValid) {
      WakeUpTimerManager.set(
        requireContext(),
        WakeUpTimerManager.Timer(
          requireNotNull(selectedPresetID),
          selectedTime,
          binding.shouldUpdateMediaVolume.isChecked,
          binding.mediaVolumeSlider.value.toInt()
        )
      )
    } else {
      WakeUpTimerManager.cancel(requireContext())
    }
  }

  private fun updateTimePicker() {
    if (changedPreset) {
      changedPreset = false
      return
    }

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

  private fun loadSelectedPresetID() {
    if (!loadSharedPrefsSelectedPresetID()) {
      loadFirstPreset()
    }
  }

  private fun loadSharedPrefsSelectedPresetID(): Boolean {
    selectedPresetID = WakeUpTimerManager.getLastUsedPresetID(requireContext())
    return selectedPresetID != null
  }

  private fun loadFirstPreset() {
    val presets = presetRepository.list()
    if (presets.isNotEmpty()) {
      selectedPresetID = presets.first().id
    }
  }


  private fun notifyScheduleLeftTime() {
    val differenceMillis = selectedTime - System.currentTimeMillis()
    if (differenceMillis < 0) {
      return // should it ever happen?
    }

    val diffHours = TimeUnit.MILLISECONDS.toHours(differenceMillis).toInt()
    val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(differenceMillis).toInt() % 60

    Snackbar.make(
      requireView(),
      getRelativeDurationString(diffHours, diffMinutes),
      Snackbar.LENGTH_LONG
    ).show()
  }

  private val matchSpacesRegex = """\s+""".toRegex()

  private fun getRelativeDurationString(hours: Int, minutes: Int): String {
    var minutePlural = ""
    if (minutes > 0 || hours == 0) {
      minutePlural = resources.getQuantityString(R.plurals.time_minutes, minutes, minutes)
    }

    var hourPlural = ""
    if (hours > 0) {
      hourPlural = resources.getQuantityString(R.plurals.time_hours, hours, hours)
    }

    var timeBridge = ""
    if (hours * minutes != 0) {
      timeBridge = getString(R.string.time_bridge)
    }

    return getString(R.string.wake_up_timer_schedule_set, hourPlural, timeBridge, minutePlural)
      .replace(matchSpacesRegex, " ")
  }

  private fun resetControls() {
    loadSelectedPresetID()
    selectedTime = 0
    binding.shouldUpdateMediaVolume.isChecked = false
    binding.mediaVolumeSlider.value =
      audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
  }
}

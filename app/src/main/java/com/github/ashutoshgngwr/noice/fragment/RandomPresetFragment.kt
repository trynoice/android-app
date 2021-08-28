package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.RandomPresetFragmentBinding
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class RandomPresetFragment : BottomSheetDialogFragment() {

  companion object {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val RANGE_INTENSITY_LIGHT = 2 until 5

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val RANGE_INTENSITY_DENSE = 3 until 8

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val RANGE_INTENSITY_ANY = 2 until 8
  }

  private lateinit var binding: RandomPresetFragmentBinding

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = RandomPresetFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    //flexCheckedButton in XML is not working so we set manually.
    binding.presetIntensityAny.isChecked = true
    binding.presetTypeAny.isChecked = true

    binding.playButton.setOnClickListener {
      val tag = when (binding.presetType.checkedRadioButtonId) {
        R.id.preset_type__focus -> Sound.Tag.FOCUS
        R.id.preset_type__relax -> Sound.Tag.RELAX
        else -> null
      }

      val intensity = when (binding.presetIntensity.checkedRadioButtonId) {
        R.id.preset_intensity__light -> RANGE_INTENSITY_LIGHT
        R.id.preset_intensity__dense -> RANGE_INTENSITY_DENSE
        else -> RANGE_INTENSITY_ANY
      }

      PlaybackController.playRandomPreset(requireContext(), tag, intensity)
      dismiss()

      // maybe show in-app review dialog to the user
      NoiceApplication.of(requireContext())
        .getReviewFlowProvider()
        .maybeAskForReview(requireActivity())
    }

    binding.cancelButton.setOnClickListener {
      dismiss()
    }

    NoiceApplication.of(requireContext())
      .getAnalyticsProvider()
      .setCurrentScreen("random_preset", RandomPresetFragment::class)
  }
}

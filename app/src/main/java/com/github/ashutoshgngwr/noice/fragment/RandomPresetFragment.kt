package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.RandomPresetFragmentBinding
import com.github.ashutoshgngwr.noice.engine.PlaybackController
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
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

  @set:Inject
  internal lateinit var presetRepository: PresetRepository

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  @set:Inject
  internal lateinit var reviewFlowProvider: ReviewFlowProvider

  @set:Inject
  internal lateinit var playbackController: PlaybackController

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
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

      // TODO: fix this
      playbackController.play(presetRepository.random(tag, intensity))
      dismiss()

      // maybe show in-app review dialog to the user
      reviewFlowProvider.maybeAskForReview(requireActivity())
    }

    binding.cancelButton.setOnClickListener {
      dismiss()
    }

    analyticsProvider.setCurrentScreen("random_preset", RandomPresetFragment::class)
  }
}

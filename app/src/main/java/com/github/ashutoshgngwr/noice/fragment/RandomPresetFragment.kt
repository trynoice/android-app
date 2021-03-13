package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.RandomPresetFragmentBinding
import com.github.ashutoshgngwr.noice.sound.Sound

class RandomPresetFragment : Fragment() {

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

    binding.playPresetButton.setOnClickListener {
      val tag = when (binding.presetType.checkedRadioButtonId) {
        R.id.preset_type__focus -> Sound.Tag.FOCUS
        R.id.preset_type__relax -> Sound.Tag.RELAX
        else -> null
      }

      val intensity = when (binding.presetIntensity.checkedRadioButtonId) {
        R.id.preset_intensity__light -> SoundLibraryFragment.RANGE_INTENSITY_LIGHT
        R.id.preset_intensity__dense -> SoundLibraryFragment.RANGE_INTENSITY_DENSE
        else -> SoundLibraryFragment.RANGE_INTENSITY_ANY
      }
      MediaPlayerService.playRandomPreset(requireContext(), tag, intensity)
      //We'll add later a if player is playing then show else there was an error
      Toast.makeText(requireContext(), "Playing Random!", Toast.LENGTH_SHORT).show()

      // Your choice to show up in-app review on this fragment!
      // maybe show in-app review dialog to the user
      //InAppReviewFlowManager.maybeAskForReview(requireActivity())
    }

    binding.cancelPresetButton.setOnClickListener {
      //What do we do cancel? Should we reset preferences button?
    }
  }
}

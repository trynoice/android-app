package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SoundPlaybackControllerFragmentBinding
import com.github.ashutoshgngwr.noice.engine.SoundPlayerManager
import com.github.ashutoshgngwr.noice.ext.launchAndRepeatOnStarted
import com.github.ashutoshgngwr.noice.service.SoundPlaybackService
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class SoundPlaybackControllerFragment : Fragment() {

  private lateinit var binding: SoundPlaybackControllerFragmentBinding
  private val viewModel: SoundPlaybackControllerViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = SoundPlaybackControllerFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    binding.playToggle.setOnLongClickListener {
      val msgResId = if (viewModel.isPlaying.value) R.string.pause else R.string.play
      Toast.makeText(requireContext(), msgResId, Toast.LENGTH_SHORT).show()
      true
    }

    binding.stop.setOnLongClickListener {
      Toast.makeText(requireContext(), R.string.stop, Toast.LENGTH_SHORT).show()
      true
    }

    binding.volume.setOnLongClickListener {
      Toast.makeText(requireContext(), R.string.main_volume, Toast.LENGTH_SHORT).show()
      true
    }

    binding.volume.setOnClickListener {
      DialogFragment.show(childFragmentManager) {
        title(R.string.main_volume)
        slider(
          viewID = R.id.volume_slider,
          to = 1F,
          step = 0.01F,
          value = (viewModel.volume.value * 100).roundToInt() / 100F, // must be a multiplier of step size.
          labelFormatter = { "${(it * 100).roundToInt()}%" },
          changeListener = { viewModel.setVolume(it) }
        )
        positiveButton(R.string.okay)
      }
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.playbackState.collect { state ->
        binding.playbackState.setText(
          when (state) {
            SoundPlayerManager.State.PLAYING -> R.string.playing
            SoundPlayerManager.State.PAUSING -> R.string.pausing
            SoundPlayerManager.State.PAUSED -> R.string.paused
            else -> R.string.stopping
          }
        )

        binding.root.isVisible = state != SoundPlayerManager.State.STOPPED
      }
    }
  }
}

@HiltViewModel
class SoundPlaybackControllerViewModel @Inject constructor(
  private val playbackServiceController: SoundPlaybackService.Controller,
) : ViewModel() {

  internal val playbackState: StateFlow<SoundPlayerManager.State> = playbackServiceController
    .getState()
    .stateIn(viewModelScope, SharingStarted.Eagerly, SoundPlayerManager.State.STOPPED)

  val isPlaying: StateFlow<Boolean> = playbackState
    .map { it == SoundPlayerManager.State.PLAYING }
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  val activePresetName: StateFlow<String?> = playbackServiceController.getCurrentPreset()
    .map { it?.name }
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  internal val volume: StateFlow<Float> = playbackServiceController.getVolume()
    .stateIn(viewModelScope, SharingStarted.Eagerly, 1F)

  val volumePercentage: StateFlow<String> = volume.map { "${(it * 100).roundToInt()}%" }
    .stateIn(viewModelScope, SharingStarted.Eagerly, "")

  fun togglePlayback() {
    if (isPlaying.value) {
      playbackServiceController.pause()
    } else {
      playbackServiceController.resume()
    }
  }

  fun stopPlayback() {
    playbackServiceController.stop()
  }

  internal fun setVolume(volume: Float) {
    playbackServiceController.setVolume(volume)
  }
}

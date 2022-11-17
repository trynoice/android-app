package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.PlaybackControllerFragmentBinding
import com.github.ashutoshgngwr.noice.engine.PlaybackController
import com.github.ashutoshgngwr.noice.engine.PlaybackState
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackControllerFragment : Fragment() {

  private lateinit var binding: PlaybackControllerFragmentBinding
  private val viewModel: PlaybackControllerViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = PlaybackControllerFragmentBinding.inflate(inflater, container, false)
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
  }
}

@HiltViewModel
class PlaybackControllerViewModel @Inject constructor(
  private val playbackController: PlaybackController,
  presetRepository: PresetRepository,
) : ViewModel() {

  val isPlaying: StateFlow<Boolean> = playbackController.getPlayerManagerState()
    .map { it == PlaybackState.PLAYING }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  val activePresetName: StateFlow<String?> = combine(
    presetRepository.listFlow(),
    playbackController.getPlayerStates(),
  ) { presets, playerStates ->
    presets.find { p -> p.hasMatchingPlayerStates(playerStates) }?.name
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  fun togglePlayback() {
    if (isPlaying.value) {
      playbackController.pause()
    } else {
      playbackController.resume()
    }
  }

  fun stopPlayback() {
    playbackController.stop()
  }
}

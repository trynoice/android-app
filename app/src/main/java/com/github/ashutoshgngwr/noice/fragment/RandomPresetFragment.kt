package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.forEach
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.RandomPresetFragmentBinding
import com.github.ashutoshgngwr.noice.databinding.RandomPresetTagChipBinding
import com.github.ashutoshgngwr.noice.ext.launchAndRepeatOnStarted
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.models.Preset
import com.github.ashutoshgngwr.noice.models.SoundTag
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.service.SoundPlaybackService
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RandomPresetFragment : BottomSheetDialogFragment() {

  private lateinit var binding: RandomPresetFragmentBinding
  private val viewModel: RandomPresetViewModel by viewModels()

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  @set:Inject
  internal lateinit var reviewFlowProvider: ReviewFlowProvider

  @set:Inject
  internal lateinit var playbackServiceController: SoundPlaybackService.Controller

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = RandomPresetFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.tags.collect { tags ->
        tags.forEach { tag ->
          val binding = RandomPresetTagChipBinding.inflate(layoutInflater, binding.tags, true)
          binding.root.text = tag.name
          binding.root.tag = tag
        }
      }
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.tagsLoadErrorStrRes
        .filterNotNull()
        .collect { causeStrRes ->
          showErrorSnackBar(getString(R.string.tags_load_error, getString(causeStrRes)))
          dismiss()
        }
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.generatedPreset
        .filterNotNull()
        .collect { preset ->
          playbackServiceController.playPreset(preset)
          dismiss()

          // maybe show in-app review dialog to the user
          reviewFlowProvider.maybeAskForReview(requireActivity())
        }
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.generatePresetErrorStrRes
        .filterNotNull()
        .collect { causeStrRes ->
          showErrorSnackBar(getString(R.string.generate_preset_error, getString(causeStrRes)))
          dismiss()
        }
    }

    binding.playButton.setOnClickListener {
      val soundCount = binding.intensity.value.toInt()
      val tags = binding.tags.getSelectedChips()
        .map { it.tag as SoundTag }
        .toSet()

      viewModel.generatePreset(tags, soundCount)
    }

    binding.cancelButton.setOnClickListener { dismiss() }
    analyticsProvider.setCurrentScreen(this::class)
  }

  private fun ChipGroup.getSelectedChips(): List<Chip> {
    val result = mutableListOf<Chip>()
    forEach { view ->
      if (view is Chip && view.isChecked) {
        result.add(view)
      }
    }

    return result
  }
}

@HiltViewModel
class RandomPresetViewModel @Inject constructor(
  soundRepository: SoundRepository,
  private val presetRepository: PresetRepository,
) : ViewModel() {

  private val tagsResource = MutableSharedFlow<Resource<List<SoundTag>>>()
  private val generatePresetResource = MutableSharedFlow<Resource<Preset>>()

  val isLoading: StateFlow<Boolean> = combine(tagsResource, generatePresetResource) { t, p ->
    t is Resource.Loading || p is Resource.Loading
  }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  val tags: StateFlow<List<SoundTag>> = tagsResource.transform { r ->
    r.data?.also { emit(it.sortedBy { t -> t.name }) }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val tagsLoadErrorStrRes: StateFlow<Int?> = tagsResource.transform { r ->
    emit(
      when {
        r.error == null || r.data?.isNotEmpty() == true -> null // cached data is fine.
        r.error is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  internal val generatePresetErrorStrRes: StateFlow<Int?> = generatePresetResource.transform { r ->
    emit(
      when (r.error) {
        null -> null
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  internal val generatedPreset: StateFlow<Preset?> = generatePresetResource.transform { r ->
    emit(r.data)
  }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  init {
    viewModelScope.launch {
      soundRepository.listTags().collect(tagsResource)
    }
  }

  fun generatePreset(tags: Set<SoundTag>, soundCount: Int) {
    viewModelScope.launch {
      presetRepository.generate(tags, soundCount).collect(generatePresetResource)
    }
  }
}

package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.navArgs
import androidx.paging.PagingDataAdapter
import androidx.paging.cachedIn
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.github.ashutoshgngwr.noice.databinding.PresetPickerFragmentBinding
import com.github.ashutoshgngwr.noice.ext.launchAndRepeatOnStarted
import com.github.ashutoshgngwr.noice.metrics.AnalyticsProvider
import com.github.ashutoshgngwr.noice.models.Preset
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@AndroidEntryPoint
class PresetPickerFragment : BottomSheetDialogFragment(),
  PresetPickerItemViewHolder.ViewController {

  @set:Inject
  internal var analyticsProvider: AnalyticsProvider? = null

  private lateinit var binding: PresetPickerFragmentBinding
  private val viewModel: PresetPickerViewModel by viewModels()
  private val args: PresetPickerFragmentArgs by navArgs()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = PresetPickerFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel

    val adapter = PresetPickerListAdapter(layoutInflater, args.selectedPreset?.id, this)
    binding.list.adapter = adapter
    launchAndRepeatOnStarted { viewModel.presetsPagingData.collectLatest(adapter::submitData) }

    binding.cancel.setOnClickListener { dismiss() }
    binding.random.setOnClickListener { setFragmentResultAndDismiss(null) }
    analyticsProvider?.setCurrentScreen(this::class)
  }

  override fun onPresetSelected(preset: Preset) {
    setFragmentResultAndDismiss(preset)
  }

  private fun setFragmentResultAndDismiss(preset: Preset?) {
    setFragmentResult(args.fragmentResultKey, bundleOf(EXTRA_SELECTED_PRESET to preset))
    dismiss()
  }

  companion object {
    const val EXTRA_SELECTED_PRESET = "selectedPreset"
  }
}

@HiltViewModel
class PresetPickerViewModel @Inject constructor(
  private val presetRepository: PresetRepository,
) : ViewModel() {

  val searchQuery = MutableStateFlow("")

  internal val presetsPagingData = searchQuery
    .flatMapLatest { presetRepository.pagingDataFlow(it) }
    .cachedIn(viewModelScope)
}

class PresetPickerListAdapter(
  private val layoutInflater: LayoutInflater,
  private val checkedPresetId: String?,
  private val viewController: PresetPickerItemViewHolder.ViewController,
) : PagingDataAdapter<Preset, PresetPickerItemViewHolder>(diffCallback) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetPickerItemViewHolder {
    val v = layoutInflater.inflate(android.R.layout.simple_list_item_single_choice, parent, false)
    require(v is CheckedTextView) { "root view must be an instance of CheckedTextView" }
    return PresetPickerItemViewHolder(v, viewController)
  }

  override fun onBindViewHolder(holder: PresetPickerItemViewHolder, position: Int) {
    val preset = getItem(position) ?: return
    holder.bind(preset, preset.id == checkedPresetId)
  }

  companion object {
    private val diffCallback = object : DiffUtil.ItemCallback<Preset>() {
      override fun areItemsTheSame(oldItem: Preset, newItem: Preset): Boolean {
        return oldItem.id == newItem.id
      }

      override fun areContentsTheSame(oldItem: Preset, newItem: Preset): Boolean {
        return oldItem == newItem
      }
    }
  }
}

class PresetPickerItemViewHolder(
  private val view: CheckedTextView,
  controller: ViewController,
) : ViewHolder(view) {

  private lateinit var preset: Preset

  init {
    view.setOnClickListener {
      view.toggle()
      controller.onPresetSelected(preset)
    }
  }

  fun bind(preset: Preset, isChecked: Boolean) {
    this.preset = preset
    view.text = preset.name
    view.isChecked = isChecked
  }

  interface ViewController {
    fun onPresetSelected(preset: Preset)
  }
}

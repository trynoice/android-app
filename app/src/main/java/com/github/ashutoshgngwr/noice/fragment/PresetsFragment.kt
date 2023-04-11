package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ShareCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.paging.cachedIn
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.AppDispatchers
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.activity.PresetShortcutHandlerActivity
import com.github.ashutoshgngwr.noice.databinding.PresetsFragmentBinding
import com.github.ashutoshgngwr.noice.databinding.PresetsListItemBinding
import com.github.ashutoshgngwr.noice.ext.launchAndRepeatOnStarted
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.ext.showSuccessSnackBar
import com.github.ashutoshgngwr.noice.models.Preset
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.service.SoundPlaybackService
import com.google.android.material.divider.MaterialDividerItemDecoration
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class PresetsFragment : Fragment(), PresetViewHolder.ViewController {

  private lateinit var binding: PresetsFragmentBinding
  private val viewModel: PresetsViewModel by viewModels()

  @set:Inject
  internal lateinit var playbackServiceController: SoundPlaybackService.Controller

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  @set:Inject
  internal lateinit var reviewFlowProvider: ReviewFlowProvider

  private val adapter by lazy { PresetListAdapter(layoutInflater, this) }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = PresetsFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.list.adapter = adapter
    MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL)
      .also { binding.list.addItemDecoration(it) }

    viewModel.refreshAppShortcuts(requireContext())
    adapter.addLoadStateListener { loadStates ->
      binding.emptyListIndicator.isVisible =
        loadStates.append.endOfPaginationReached && adapter.itemCount < 1
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.presetsPagingData.collectLatest(adapter::submitData)
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.presetsWithAppShortcut.collect(adapter::setPresetsWithAppShortcut)
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.activePreset
        .map { it?.id }
        .collect(adapter::setActivePresetId)
    }

    analyticsProvider.setCurrentScreen("presets", PresetsFragment::class)
  }

  override fun onPresetPlayToggleClicked(preset: Preset) {
    if (viewModel.activePreset.value?.id == preset.id) {
      playbackServiceController.stop()
    } else {
      playbackServiceController.playPreset(preset)
    }
  }

  override fun onPresetShareClicked(preset: Preset) {
    val url = viewModel.getShareableUrl(preset)
    ShareCompat.IntentBuilder(binding.root.context)
      .setType("text/plain")
      .setChooserTitle(R.string.share)
      .setText(url)
      .startChooser()

    analyticsProvider.logEvent("share_preset_uri", bundleOf("item_length" to url.length))
  }

  override fun onPresetDeleteClicked(preset: Preset) {
    val params = bundleOf("success" to false)
    DialogFragment.show(childFragmentManager) {
      title(R.string.delete)
      message(R.string.preset_delete_confirmation, preset.name)
      negativeButton(R.string.cancel)
      positiveButton(R.string.delete) {
        viewModel.delete(preset.id)
        // then stop playback if recently deleted preset was playing
        if (viewModel.activePreset.value?.id == preset.id) {
          playbackServiceController.stop()
        }

        ShortcutManagerCompat.removeDynamicShortcuts(requireContext(), listOf(preset.id))
        showSuccessSnackBar(R.string.preset_deleted, snackBarAnchorView())

        params.putBoolean("success", true)
        reviewFlowProvider.maybeAskForReview(requireActivity()) // maybe show in-app review dialog to the user
      }

      onDismiss { analyticsProvider.logEvent("preset_delete", params) }
    }
  }

  override fun onPresetRenameClicked(preset: Preset) {
    DialogFragment.show(childFragmentManager) {
      title(R.string.rename)
      val nameGetter = input(
        hintRes = R.string.name,
        preFillValue = preset.name,
        validator = { name ->
          when {
            name.isBlank() -> R.string.preset_name_cannot_be_empty
            name == preset.name -> 0
            viewModel.doesPresetExistsByName(name) -> R.string.preset_already_exists
            else -> 0
          }
        }
      )

      negativeButton(R.string.cancel)
      positiveButton(R.string.save) {
        viewModel.save(preset.copy(name = nameGetter.invoke()))
        // maybe show in-app review dialog to the user
        reviewFlowProvider.maybeAskForReview(requireActivity())
      }
    }
  }

  override fun onPresetCreatePinnedShortcutClicked(preset: Preset) {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())) {
      showErrorSnackBar(R.string.pinned_shortcuts_not_supported, snackBarAnchorView())
      return
    }

    val info = buildShortcutInfo(UUID.randomUUID().toString(), "pinned", preset)
    val result =
      ShortcutManagerCompat.requestPinShortcut(requireContext(), info, null)
    if (!result) {
      showErrorSnackBar(R.string.pinned_shortcut_creation_failed, snackBarAnchorView())
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      showSuccessSnackBar(R.string.pinned_shortcut_created, snackBarAnchorView())
    }

    val params = bundleOf("success" to result, "shortcut_type" to "pinned")
    analyticsProvider.logEvent("preset_shortcut_create", params)
  }

  override fun onPresetCreateAppShortcutClicked(preset: Preset) {
    val list = ShortcutManagerCompat.getDynamicShortcuts(requireContext())
    list.add(buildShortcutInfo(preset.id, "app", preset))
    val result = ShortcutManagerCompat.addDynamicShortcuts(requireContext(), list)
    if (result) {
      showSuccessSnackBar(R.string.app_shortcut_created, snackBarAnchorView())
    } else {
      showErrorSnackBar(R.string.app_shortcut_creation_failed, snackBarAnchorView())
    }

    viewModel.refreshAppShortcuts(requireContext())
    val params = bundleOf("success" to result, "shortcut_type" to "app")
    analyticsProvider.logEvent("preset_shortcut_create", params)
  }

  override fun onPresetRemoveAppShortcutClicked(preset: Preset) {
    ShortcutManagerCompat.removeDynamicShortcuts(requireContext(), listOf(preset.id))
    showSuccessSnackBar(R.string.app_shortcut_removed, snackBarAnchorView())
    viewModel.refreshAppShortcuts(requireContext())
    analyticsProvider.logEvent("preset_shortcut_remove", bundleOf("shortcut_type" to "app"))
  }

  private fun buildShortcutInfo(id: String, type: String, preset: Preset): ShortcutInfoCompat {
    return ShortcutInfoCompat.Builder(requireContext(), id).run {
      setShortLabel(preset.name)
      setIcon(IconCompat.createWithResource(requireContext(), R.mipmap.ic_preset_shortcut))
      setIntent(
        Intent(requireContext(), PresetShortcutHandlerActivity::class.java)
          .setAction(Intent.ACTION_VIEW)
          .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
          .putExtra(PresetShortcutHandlerActivity.EXTRA_SHORTCUT_ID, id)
          .putExtra(PresetShortcutHandlerActivity.EXTRA_SHORTCUT_TYPE, type)
          .putExtra(PresetShortcutHandlerActivity.EXTRA_PRESET_ID, preset.id)
      )

      build()
    }
  }

  private fun snackBarAnchorView(): View? {
    return activity?.findViewById<View?>(R.id.playback_controller)
      ?.takeIf { it.isVisible }
  }
}

@HiltViewModel
class PresetsViewModel @Inject constructor(
  private val presetRepository: PresetRepository,
  playbackServiceController: SoundPlaybackService.Controller,
  private val appDispatchers: AppDispatchers,
) : ViewModel() {

  internal val presetsPagingData: Flow<PagingData<Preset>> = presetRepository.pagingDataFlow()
    .cachedIn(viewModelScope)

  internal val activePreset: StateFlow<Preset?> = playbackServiceController.getCurrentPreset()
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  internal val presetsWithAppShortcut = MutableStateFlow(emptySet<String>())

  // loading dynamic shortcuts on the IO thread because laoding them on the main thread sometimes
  // causes an ANR.
  internal fun refreshAppShortcuts(context: Context) = viewModelScope.launch(appDispatchers.io) {
    ShortcutManagerCompat.getDynamicShortcuts(context)
      .map { it.id }
      .toSet()
      .also { presetsWithAppShortcut.emit(it) }
  }

  internal fun getShareableUrl(preset: Preset): String {
    return presetRepository.writeAsUrl(preset)
  }

  internal suspend fun doesPresetExistsByName(name: String): Boolean {
    return presetRepository.existsByName(name)
  }

  internal fun save(preset: Preset) {
    viewModelScope.launch { presetRepository.save(preset) }
  }

  internal fun delete(presetId: String) {
    viewModelScope.launch { presetRepository.delete(presetId) }
  }
}

class PresetListAdapter(
  private val layoutInflater: LayoutInflater,
  private val viewController: PresetViewHolder.ViewController,
) : PagingDataAdapter<Preset, PresetViewHolder>(diffCallback) {

  private var presetsWithAppShortcut = emptySet<String>()
  private var activePresetId: String? = null

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetViewHolder {
    val binding = PresetsListItemBinding.inflate(layoutInflater, parent, false)
    return PresetViewHolder(binding, viewController)
  }

  override fun onBindViewHolder(holder: PresetViewHolder, position: Int) {
    val preset = getItem(position) ?: return
    holder.bind(preset, preset.id in presetsWithAppShortcut, activePresetId == preset.id)
  }

  fun setPresetsWithAppShortcut(presetIds: Set<String>) {
    val oldPresetIds = presetsWithAppShortcut
    presetsWithAppShortcut = presetIds
    repeat(itemCount) { i ->
      val preset = getItem(i)
      if ((preset?.id in oldPresetIds) != (preset?.id in presetIds)) {
        notifyItemChanged(i)
      }
    }
  }

  fun setActivePresetId(id: String?) {
    if (activePresetId == id) {
      return
    }

    var oldActivePos = -1
    var newActivePos = -1
    repeat(itemCount) { i ->
      val preset = getItem(i)
      if (activePresetId == preset?.id) oldActivePos = i
      if (id == preset?.id) newActivePos = i
    }

    activePresetId = id
    if (oldActivePos > -1) {
      notifyItemChanged(oldActivePos)
    }

    if (newActivePos > -1) {
      notifyItemChanged(newActivePos)
    }
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

class PresetViewHolder(
  private val binding: PresetsListItemBinding,
  private val controller: ViewController,
) : RecyclerView.ViewHolder(binding.root) {

  private lateinit var preset: Preset
  private var hasAppShortcut = false

  init {
    binding.playButton.setOnClickListener {
      controller.onPresetPlayToggleClicked(preset)
    }

    val onMenuItemClickListener = PopupMenu.OnMenuItemClickListener {
      when (it.itemId) {
        R.id.action_share -> controller.onPresetShareClicked(preset)
        R.id.action_delete -> controller.onPresetDeleteClicked(preset)
        R.id.action_rename -> controller.onPresetRenameClicked(preset)
        R.id.action_create_pinned_shortcut -> controller.onPresetCreatePinnedShortcutClicked(preset)
        R.id.action_create_app_shortcut -> controller.onPresetCreateAppShortcutClicked(preset)
        R.id.action_remove_app_shortcut -> controller.onPresetRemoveAppShortcutClicked(preset)
      }

      true
    }

    binding.menuButton.setOnClickListener {
      PopupMenu(binding.root.context, binding.menuButton).let { pm ->
        pm.inflate(R.menu.preset)
        pm.menu.findItem(R.id.action_create_app_shortcut).isVisible = !hasAppShortcut
        pm.menu.findItem(R.id.action_remove_app_shortcut).isVisible = hasAppShortcut
        pm.setOnMenuItemClickListener(onMenuItemClickListener)
        pm.show()
      }
    }
  }

  fun bind(preset: Preset, hasAppShortcut: Boolean, isPlaying: Boolean) {
    this.preset = preset
    this.hasAppShortcut = hasAppShortcut
    binding.title.text = preset.name
    binding.title.isSelected = true
    binding.playButton.isChecked = isPlaying
  }


  interface ViewController {
    fun onPresetPlayToggleClicked(preset: Preset)
    fun onPresetShareClicked(preset: Preset)
    fun onPresetDeleteClicked(preset: Preset)
    fun onPresetRenameClicked(preset: Preset)
    fun onPresetCreatePinnedShortcutClicked(preset: Preset)
    fun onPresetCreateAppShortcutClicked(preset: Preset)
    fun onPresetRemoveAppShortcutClicked(preset: Preset)
  }
}

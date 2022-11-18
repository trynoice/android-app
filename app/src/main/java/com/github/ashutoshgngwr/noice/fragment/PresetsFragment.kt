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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.activity.PresetShortcutHandlerActivity
import com.github.ashutoshgngwr.noice.databinding.PresetsFragmentBinding
import com.github.ashutoshgngwr.noice.databinding.PresetsListItemBinding
import com.github.ashutoshgngwr.noice.engine.PlaybackController
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.ext.showSuccessSnackBar
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import kotlin.math.min

@AndroidEntryPoint
class PresetsFragment : Fragment(), PresetListItemController {

  private lateinit var binding: PresetsFragmentBinding
  private val viewModel: PresetsViewModel by viewModels()

  @set:Inject
  internal lateinit var presetRepository: PresetRepository

  @set:Inject
  internal lateinit var playbackController: PlaybackController

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
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    binding.list.adapter = adapter
    val itemDecor = DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
    binding.list.addItemDecoration(itemDecor)
    viewModel.loadAppShortcuts(requireContext())
    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.presets.collect(adapter::setPresets)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.presetsWithAppShortcut.collect(adapter::setPresetsWithAppShortcut)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.activePresetId.collect(adapter::setActivePresetId)
    }

    analyticsProvider.setCurrentScreen("presets", PresetsFragment::class)
  }

  override fun onPlaybackToggled(preset: Preset) {
    if (viewModel.activePresetId.value == preset.id) {
      playbackController.stop()
    } else {
      playbackController.play(preset)
    }
  }

  override fun onShareClicked(preset: Preset) {
    val url = presetRepository.writeAsUrl(preset)
    ShareCompat.IntentBuilder(binding.root.context)
      .setType("text/plain")
      .setChooserTitle(R.string.share)
      .setText(url)
      .startChooser()

    analyticsProvider.logEvent("share_preset_uri", bundleOf("item_length" to url.length))
  }

  override fun onDeleteClicked(preset: Preset) {
    val params = bundleOf("success" to false)
    DialogFragment.show(childFragmentManager) {
      title(R.string.delete)
      message(R.string.preset_delete_confirmation, preset.name)
      negativeButton(R.string.cancel)
      positiveButton(R.string.delete) {
        presetRepository.delete(preset.id)
        // then stop playback if recently deleted preset was playing
        if (viewModel.activePresetId.value == preset.id) {
          playbackController.stop()
        }

        ShortcutManagerCompat.removeDynamicShortcuts(requireContext(), listOf(preset.id))
        showSuccessSnackBar(R.string.preset_deleted, snackBarAnchorView())

        params.putBoolean("success", true)
        reviewFlowProvider.maybeAskForReview(requireActivity()) // maybe show in-app review dialog to the user
      }

      onDismiss { analyticsProvider.logEvent("preset_delete", params) }
    }
  }

  override fun onRenameClicked(preset: Preset) {
    DialogFragment.show(childFragmentManager) {
      val presets = presetRepository.list()
      title(R.string.rename)
      val nameGetter = input(
        hintRes = R.string.name,
        preFillValue = preset.name,
        validator = { name ->
          when {
            name.isBlank() -> R.string.preset_name_cannot_be_empty
            name == preset.name -> 0
            presets.any { it.name == name } -> R.string.preset_already_exists
            else -> 0
          }
        }
      )

      negativeButton(R.string.cancel)
      positiveButton(R.string.save) {
        presetRepository.update(preset.copy(name = nameGetter.invoke()))
        // maybe show in-app review dialog to the user
        reviewFlowProvider.maybeAskForReview(requireActivity())
      }
    }
  }

  override fun onCreatePinnedShortcutClicked(preset: Preset) {
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

  override fun onCreateAppShortcutClicked(preset: Preset) {
    val list = ShortcutManagerCompat.getDynamicShortcuts(requireContext())
    list.add(buildShortcutInfo(preset.id, "app", preset))
    val result = ShortcutManagerCompat.addDynamicShortcuts(requireContext(), list)
    if (result) {
      showSuccessSnackBar(R.string.app_shortcut_created, snackBarAnchorView())
    } else {
      showErrorSnackBar(R.string.app_shortcut_creation_failed, snackBarAnchorView())
    }

    viewModel.loadAppShortcuts(requireContext())
    val params = bundleOf("success" to result, "shortcut_type" to "app")
    analyticsProvider.logEvent("preset_shortcut_create", params)
  }

  override fun onRemoveAppShortcutClicked(preset: Preset) {
    ShortcutManagerCompat.removeDynamicShortcuts(requireContext(), listOf(preset.id))
    showSuccessSnackBar(R.string.app_shortcut_removed, snackBarAnchorView())
    viewModel.loadAppShortcuts(requireContext())
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
  presetRepository: PresetRepository,
  playbackController: PlaybackController,
) : ViewModel() {

  internal val presets: StateFlow<List<Preset>> = presetRepository.listFlow()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

  internal val activePresetId: StateFlow<String?> =
    combine(presets, playbackController.getPlayerStates()) { presets, playerStates ->
      presets.find { p -> p.hasMatchingPlayerStates(playerStates) }?.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  internal val presetsWithAppShortcut = MutableStateFlow(emptySet<String>())

  val isEmptyIndicatorVisible: StateFlow<Boolean> = presets.transform { presets ->
    emit(presets.isEmpty())
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

  internal fun loadAppShortcuts(context: Context) {
    presetsWithAppShortcut.value = ShortcutManagerCompat.getDynamicShortcuts(context)
      .map { it.id }
      .toSet()
  }
}

class PresetListAdapter(
  private val layoutInflater: LayoutInflater,
  private val itemController: PresetListItemController,
) : RecyclerView.Adapter<PresetViewHolder>() {

  private var presets = emptyList<Preset>()
  private var presetsWithAppShortcut = emptySet<String>()
  private var activePresetId: String? = null

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetViewHolder {
    val binding = PresetsListItemBinding.inflate(layoutInflater, parent, false)
    return PresetViewHolder(binding, itemController)
  }

  override fun getItemCount(): Int {
    return presets.size
  }

  override fun onBindViewHolder(holder: PresetViewHolder, position: Int) {
    val presetId = presets[position].id
    holder.bind(presets[position], presetId in presetsWithAppShortcut, activePresetId == presetId)
  }

  fun setPresets(presets: List<Preset>) {
    // since the dataset is homogeneous, we can update the recycler view items without removing all
    // items and reinserting them.
    val updated = this.presets.size
    this.presets = presets
    notifyItemRangeChanged(0, min(updated, presets.size))
    if (updated > presets.size) {
      notifyItemRangeRemoved(updated, updated - presets.size)
    } else {
      notifyItemRangeInserted(updated, presets.size - updated)
    }
  }

  fun setPresetsWithAppShortcut(presetIds: Set<String>) {
    val oldPresetIds = presetsWithAppShortcut
    presetsWithAppShortcut = presetIds
    presets.forEachIndexed { i, preset ->
      if ((preset.id in oldPresetIds) != (preset.id in presetIds)) {
        notifyItemChanged(i)
      }
    }
  }

  fun setActivePresetId(id: String?) {
    val oldActivePos = presets.indexOfFirst { it.id == activePresetId }
    val newActivePos = presets.indexOfFirst { it.id == id }
    activePresetId = id
    if (oldActivePos > -1) {
      notifyItemChanged(oldActivePos)
    }

    if (newActivePos > -1) {
      notifyItemChanged(newActivePos)
    }
  }
}

class PresetViewHolder(
  private val binding: PresetsListItemBinding,
  private val controller: PresetListItemController,
) : RecyclerView.ViewHolder(binding.root) {

  private lateinit var preset: Preset
  private var hasAppShortcut = false

  init {
    binding.playButton.setOnClickListener {
      controller.onPlaybackToggled(preset)
    }

    val onMenuItemClickListener = PopupMenu.OnMenuItemClickListener {
      when (it.itemId) {
        R.id.action_share -> controller.onShareClicked(preset)
        R.id.action_delete -> controller.onDeleteClicked(preset)
        R.id.action_rename -> controller.onRenameClicked(preset)
        R.id.action_create_pinned_shortcut -> controller.onCreatePinnedShortcutClicked(preset)
        R.id.action_create_app_shortcut -> controller.onCreateAppShortcutClicked(preset)
        R.id.action_remove_app_shortcut -> controller.onRemoveAppShortcutClicked(preset)
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
}

interface PresetListItemController {
  fun onPlaybackToggled(preset: Preset)
  fun onShareClicked(preset: Preset)
  fun onDeleteClicked(preset: Preset)
  fun onRenameClicked(preset: Preset)
  fun onCreatePinnedShortcutClicked(preset: Preset)
  fun onCreateAppShortcutClicked(preset: Preset)
  fun onRemoveAppShortcutClicked(preset: Preset)
}

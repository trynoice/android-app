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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.WakeUpTimerManager
import com.github.ashutoshgngwr.noice.activity.ShortcutHandlerActivity
import com.github.ashutoshgngwr.noice.databinding.PresetsFragmentBinding
import com.github.ashutoshgngwr.noice.databinding.PresetsListItemBinding
import com.github.ashutoshgngwr.noice.engine.PlaybackController
import com.github.ashutoshgngwr.noice.engine.PlaybackState
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.ext.showSuccessSnackbar
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.SoundRepository
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
  internal lateinit var wakeUpTimerManager: WakeUpTimerManager

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
      viewModel.presetListItems.collect(adapter::setItems)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.activePresetId.collect(adapter::setActivePresetId)
    }

    analyticsProvider.setCurrentScreen("presets", PresetsFragment::class)
  }

  override fun onPlaybackToggled(item: PresetListItem) {
    if (viewModel.activePresetId.value == item.preset.id) {
      playbackController.stop()
    } else {
      playbackController.play(item.preset)
    }
  }

  override fun onShareClicked(item: PresetListItem) {
    val url = presetRepository.writeAsUrl(item.preset)
    ShareCompat.IntentBuilder(binding.root.context)
      .setType("text/plain")
      .setChooserTitle(R.string.share)
      .setText(url)
      .startChooser()

    analyticsProvider.logEvent("share_preset_uri", bundleOf("item_length" to url.length))
  }

  override fun onDeleteClicked(item: PresetListItem) {
    val params = bundleOf("success" to false)
    DialogFragment.show(childFragmentManager) {
      title(R.string.delete)
      message(R.string.preset_delete_confirmation, item.preset.name)
      negativeButton(R.string.cancel)
      positiveButton(R.string.delete) {
        presetRepository.delete(item.preset.id)
        // then stop playback if recently deleted preset was playing
        if (viewModel.activePresetId.value == item.preset.id) {
          playbackController.stop()
        }

        // cancel wake-up timer if it is set to the deleted preset.
        if (item.preset.id == wakeUpTimerManager.get()?.presetID) {
          wakeUpTimerManager.cancel()
        }

        ShortcutManagerCompat.removeDynamicShortcuts(requireContext(), listOf(item.preset.id))
        showSuccessSnackbar(R.string.preset_deleted)

        params.putBoolean("success", true)
        reviewFlowProvider.maybeAskForReview(requireActivity()) // maybe show in-app review dialog to the user
      }

      onDismiss { analyticsProvider.logEvent("preset_delete", params) }
    }
  }

  override fun onRenameClicked(item: PresetListItem) {
    DialogFragment.show(childFragmentManager) {
      val presets = presetRepository.list()
      title(R.string.rename)
      val nameGetter = input(
        hintRes = R.string.name,
        preFillValue = item.preset.name,
        validator = { name ->
          when {
            name.isBlank() -> R.string.preset_name_cannot_be_empty
            name == item.preset.name -> 0
            presets.any { it.name == name } -> R.string.preset_already_exists
            else -> 0
          }
        }
      )

      negativeButton(R.string.cancel)
      positiveButton(R.string.save) {
        presetRepository.update(item.preset.copy(name = nameGetter.invoke()))
        // maybe show in-app review dialog to the user
        reviewFlowProvider.maybeAskForReview(requireActivity())
      }
    }
  }

  override fun onCreatePinnedShortcutClicked(item: PresetListItem) {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())) {
      showErrorSnackbar(R.string.pinned_shortcuts_not_supported)
      return
    }

    val info = buildShortcutInfo(UUID.randomUUID().toString(), "pinned", item.preset)
    val result =
      ShortcutManagerCompat.requestPinShortcut(requireContext(), info, null)
    if (!result) {
      showErrorSnackbar(R.string.pinned_shortcut_creation_failed)
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      showSuccessSnackbar(R.string.pinned_shortcut_created)
    }

    val params = bundleOf("success" to result, "shortcut_type" to "pinned")
    analyticsProvider.logEvent("preset_shortcut_create", params)
  }

  override fun onCreateAppShortcutClicked(item: PresetListItem) {
    val list = ShortcutManagerCompat.getDynamicShortcuts(requireContext())
    list.add(buildShortcutInfo(item.preset.id, "app", item.preset))
    val result = ShortcutManagerCompat.addDynamicShortcuts(requireContext(), list)
    if (result) {
      showSuccessSnackbar(R.string.app_shortcut_created)
    } else {
      showErrorSnackbar(R.string.app_shortcut_creation_failed)
    }

    viewModel.loadAppShortcuts(requireContext())
    val params = bundleOf("success" to result, "shortcut_type" to "app")
    analyticsProvider.logEvent("preset_shortcut_create", params)
  }

  override fun onRemoveAppShortcutClicked(item: PresetListItem) {
    ShortcutManagerCompat.removeDynamicShortcuts(requireContext(), listOf(item.preset.id))
    showSuccessSnackbar(R.string.app_shortcut_removed)
    viewModel.loadAppShortcuts(requireContext())
    analyticsProvider.logEvent("preset_shortcut_remove", bundleOf("shortcut_type" to "app"))
  }

  private fun buildShortcutInfo(id: String, type: String, preset: Preset): ShortcutInfoCompat {
    return ShortcutInfoCompat.Builder(requireContext(), id).run {
      setShortLabel(preset.name)
      setIcon(IconCompat.createWithResource(requireContext(), R.mipmap.ic_preset_shortcut))
      setIntent(
        Intent(requireContext(), ShortcutHandlerActivity::class.java)
          .setAction(Intent.ACTION_VIEW)
          .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
          .putExtra(ShortcutHandlerActivity.EXTRA_SHORTCUT_ID, id)
          .putExtra(ShortcutHandlerActivity.EXTRA_SHORTCUT_TYPE, type)
          .putExtra(ShortcutHandlerActivity.EXTRA_PRESET_ID, preset.id)
      )

      build()
    }
  }
}

@HiltViewModel
class PresetsViewModel @Inject constructor(
  presetRepository: PresetRepository,
  soundRepository: SoundRepository,
) : ViewModel() {

  private val presets: StateFlow<List<Preset>> = presetRepository.listFlow()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

  internal val activePresetId: StateFlow<String?> =
    combine(presets, soundRepository.getPlayerStates()) { presets, playerStates ->
      playerStates
        // exclude stopping and stopped players from active preset
        .filterNot { it.playbackState.oneOf(PlaybackState.STOPPING, PlaybackState.STOPPED) }
        .toTypedArray()
        .let { s -> presets.find { p -> p.hasMatchingPlayerStates(s) } }
        ?.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  private val appShortcuts = MutableStateFlow(emptyList<ShortcutInfoCompat>())

  internal val presetListItems: StateFlow<List<PresetListItem>> =
    combine(presets, appShortcuts) { presets, appShortcuts ->
      val items = mutableListOf<PresetListItem>()
      presets.forEach { preset ->
        val hasAppShortcut = appShortcuts.any { it.id == preset.id }
        items.add(PresetListItem(preset, hasAppShortcut))
      }

      items
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

  val isEmptyIndicatorVisible: StateFlow<Boolean> = presets.transform { presets ->
    emit(presets.isEmpty())
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

  internal fun loadAppShortcuts(context: Context) {
    appShortcuts.value = ShortcutManagerCompat.getDynamicShortcuts(context)
  }
}

class PresetListAdapter(
  private val layoutInflater: LayoutInflater,
  private val itemController: PresetListItemController,
) : RecyclerView.Adapter<PresetViewHolder>() {

  private val items = mutableListOf<PresetListItem>()
  private var activePresetId: String? = null

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetViewHolder {
    val binding = PresetsListItemBinding.inflate(layoutInflater, parent, false)
    return PresetViewHolder(binding, itemController)
  }

  override fun getItemCount(): Int {
    return items.size
  }

  override fun onBindViewHolder(holder: PresetViewHolder, position: Int) {
    holder.bind(items[position], activePresetId == items[position].preset.id)
  }

  fun setItems(items: List<PresetListItem>) {
    // since the dataset is homogeneous, we can update the recycler view items without removing all
    // items and reinserting them.
    val updated = this.items.size
    this.items.clear()
    this.items.addAll(items)
    notifyItemRangeChanged(0, min(updated, items.size))
    if (updated > items.size) {
      notifyItemRangeRemoved(updated, updated - items.size)
    } else {
      notifyItemRangeInserted(updated, items.size - updated)
    }
  }

  fun setActivePresetId(id: String?) {
    val oldActivePos = items.indexOfFirst { it.preset.id == activePresetId }
    val newActivePos = items.indexOfFirst { it.preset.id == id }
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

  private lateinit var item: PresetListItem

  init {
    binding.playButton.setOnClickListener {
      controller.onPlaybackToggled(item)
    }

    val onMenuItemClickListener = PopupMenu.OnMenuItemClickListener {
      when (it.itemId) {
        R.id.action_share -> controller.onShareClicked(item)
        R.id.action_delete -> controller.onDeleteClicked(item)
        R.id.action_rename -> controller.onRenameClicked(item)
        R.id.action_create_pinned_shortcut -> controller.onCreatePinnedShortcutClicked(item)
        R.id.action_create_app_shortcut -> controller.onCreateAppShortcutClicked(item)
        R.id.action_remove_app_shortcut -> controller.onRemoveAppShortcutClicked(item)
      }

      true
    }

    binding.menuButton.setOnClickListener {
      PopupMenu(binding.root.context, binding.menuButton).let { pm ->
        pm.inflate(R.menu.preset)
        pm.menu.findItem(R.id.action_create_app_shortcut).isVisible = !item.hasAppShortcut
        pm.menu.findItem(R.id.action_remove_app_shortcut).isVisible = item.hasAppShortcut
        pm.setOnMenuItemClickListener(onMenuItemClickListener)
        pm.show()
      }
    }
  }

  fun bind(item: PresetListItem, isPlaying: Boolean) {
    this.item = item
    binding.title.text = item.preset.name
    binding.playButton.isChecked = isPlaying
  }
}

interface PresetListItemController {
  fun onPlaybackToggled(item: PresetListItem)
  fun onShareClicked(item: PresetListItem)
  fun onDeleteClicked(item: PresetListItem)
  fun onRenameClicked(item: PresetListItem)
  fun onCreatePinnedShortcutClicked(item: PresetListItem)
  fun onCreateAppShortcutClicked(item: PresetListItem)
  fun onRemoveAppShortcutClicked(item: PresetListItem)
}

data class PresetListItem(
  val preset: Preset,
  val hasAppShortcut: Boolean,
)

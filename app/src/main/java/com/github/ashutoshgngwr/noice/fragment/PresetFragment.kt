package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ShareCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.WakeUpTimerManager
import com.github.ashutoshgngwr.noice.activity.ShortcutHandlerActivity
import com.github.ashutoshgngwr.noice.databinding.PresetListFragmentBinding
import com.github.ashutoshgngwr.noice.databinding.PresetListItemBinding
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.google.android.material.snackbar.Snackbar
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

class PresetFragment : Fragment() {

  private lateinit var binding: PresetListFragmentBinding
  private lateinit var presetRepository: PresetRepository

  private var adapter: PresetListAdapter? = null
  private var activePresetPos = -1
  private var dataSet = mutableListOf<Preset>()

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = PresetListFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    presetRepository = PresetRepository.newInstance(requireContext())
    dataSet = presetRepository.list().toMutableList()
    adapter = PresetListAdapter(requireContext())
    binding.presetList.also {
      it.adapter = adapter
      it.setHasFixedSize(true)
      it.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
    }

    EventBus.getDefault().register(this)
    updateEmptyListIndicatorVisibility()
  }

  override fun onDestroyView() {
    EventBus.getDefault().unregister(this)
    super.onDestroyView()
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onPlayerManagerUpdate(event: MediaPlayerService.PlaybackUpdateEvent) {
    val oldPresetPos = activePresetPos
    activePresetPos = Preset.from("", event.players.values).let { dataSet.indexOf(it) }
    adapter?.notifyItemChanged(oldPresetPos)
    adapter?.notifyItemChanged(activePresetPos)
  }

  private fun updateEmptyListIndicatorVisibility() {
    if (adapter?.itemCount ?: 0 > 0) {
      binding.emptyListHint.visibility = View.GONE
    } else {
      binding.emptyListHint.visibility = View.VISIBLE
    }
  }

  inner class PresetListAdapter(context: Context) : RecyclerView.Adapter<ViewHolder>() {

    private val layoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      return ViewHolder(PresetListItemBinding.inflate(layoutInflater, parent, false))
    }

    override fun getItemCount(): Int {
      return dataSet.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      holder.binding.title.text = dataSet[position].name
      holder.binding.playButton.isChecked = position == activePresetPos
    }
  }

  inner class ViewHolder(val binding: PresetListItemBinding) :
    RecyclerView.ViewHolder(binding.root) {

    init {
      binding.playButton.setOnClickListener {
        if (adapterPosition != activePresetPos) {
          PlaybackController.playPreset(requireContext(), dataSet[adapterPosition].id)
        } else {
          PlaybackController.stop(requireContext())
        }
      }

      val onMenuItemClickListener = PopupMenu.OnMenuItemClickListener {
        when (it.itemId) {
          R.id.action_share -> showShareIntentSender()
          R.id.action_delete -> showDeletePresetConfirmation()
          R.id.action_rename -> showRenamePresetInput()
          R.id.action_create_pinned_shortcut -> createPinnedShortcut()
          R.id.action_create_app_shortcut -> createAppShortcut()
          R.id.action_remove_app_shortcut -> removeAppShortcut()
        }

        true
      }

      binding.menuButton.setOnClickListener {
        PopupMenu(requireContext(), binding.menuButton).let {
          it.inflate(R.menu.preset)
          val hasAppShortcut = hasAppShortcut()
          it.menu.findItem(R.id.action_create_app_shortcut).isVisible = !hasAppShortcut
          it.menu.findItem(R.id.action_remove_app_shortcut).isVisible = hasAppShortcut
          it.setOnMenuItemClickListener(onMenuItemClickListener)
          it.show()
        }
      }
    }

    private fun showShareIntentSender() {
      ShareCompat.IntentBuilder.from(requireActivity())
        .setType("text/plain")
        .setChooserTitle(R.string.share)
        .setText(dataSet[adapterPosition].toUri().toString())
        .startChooser()
    }

    private fun createPinnedShortcut() {
      if (!ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())) {
        showSnackBar(R.string.pinned_shortcuts_not_supported)
        return
      }

      val info = buildShortcutInfo(UUID.randomUUID().toString())
      val result = ShortcutManagerCompat.requestPinShortcut(requireContext(), info, null)
      if (!result) {
        showSnackBar(R.string.pinned_shortcut_creation_failed)
      } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        showSnackBar(R.string.pinned_shortcut_created)
      }
    }

    private fun createAppShortcut() {
      val list = ShortcutManagerCompat.getDynamicShortcuts(requireContext())
      val presetID = dataSet[adapterPosition].id
      list.add(buildShortcutInfo(presetID))

      if (ShortcutManagerCompat.addDynamicShortcuts(requireContext(), list)) {
        showSnackBar(R.string.app_shortcut_created)
      } else {
        showSnackBar(R.string.app_shortcut_creation_failed)
      }
    }

    private fun removeAppShortcut() {
      val presetID = dataSet[adapterPosition].id
      ShortcutManagerCompat.removeDynamicShortcuts(requireContext(), listOf(presetID))
      showSnackBar(R.string.app_shortcut_removed)
    }

    private fun hasAppShortcut(): Boolean {
      ShortcutManagerCompat.getDynamicShortcuts(requireContext()).forEach {
        if (it.id == dataSet[adapterPosition].id) {
          return true
        }
      }

      return false
    }

    private fun buildShortcutInfo(shortcutID: String): ShortcutInfoCompat {
      return with(ShortcutInfoCompat.Builder(requireContext(), shortcutID)) {
        setShortLabel(dataSet[adapterPosition].name)
        setIcon(IconCompat.createWithResource(requireContext(), R.mipmap.ic_preset_shortcut))
        setIntent(
          Intent(requireContext(), ShortcutHandlerActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(ShortcutHandlerActivity.EXTRA_SHORTCUT_ID, shortcutID)
            .putExtra(ShortcutHandlerActivity.EXTRA_PRESET_ID, dataSet[adapterPosition].id)
        )

        build()
      }
    }

    private fun showRenamePresetInput() {
      DialogFragment.show(childFragmentManager) {
        title(R.string.rename)
        input(
          hintRes = R.string.name,
          preFillValue = dataSet[adapterPosition].name,
          validator = {
            when {
              it.isBlank() -> R.string.preset_name_cannot_be_empty
              dataSet[adapterPosition].name == it -> 0 // no error if the name didn't change
              dataSet.any { p -> it == p.name } -> R.string.preset_already_exists
              else -> 0
            }
          }
        )

        negativeButton(R.string.cancel)
        positiveButton(R.string.save) {
          dataSet[adapterPosition].name = getInputText()
          presetRepository.update(dataSet[adapterPosition])
          adapter?.notifyItemChanged(adapterPosition)
          PlaybackController.requestUpdateEvent(requireContext())

          // maybe show in-app review dialog to the user
          ReviewFlowProvider.of(requireContext()).maybeAskForReview(requireActivity())
        }
      }
    }

    private fun showDeletePresetConfirmation() {
      DialogFragment.show(requireActivity().supportFragmentManager) {
        title(R.string.delete)
        message(R.string.preset_delete_confirmation, dataSet[adapterPosition].name)
        negativeButton(R.string.cancel)
        positiveButton(R.string.delete) {
          val preset = dataSet.removeAt(adapterPosition)
          presetRepository.delete(preset.id)
          // then stop playback if recently deleted preset was playing
          if (adapterPosition == activePresetPos) {
            PlaybackController.stop(requireContext())
          }

          if (adapterPosition < activePresetPos) {
            activePresetPos -= 1 // account for recent deletion
          }

          cancelWakeUpTimerIfScheduled(preset.id)
          ShortcutManagerCompat.removeDynamicShortcuts(requireContext(), listOf(preset.id))
          adapter?.notifyItemRemoved(adapterPosition)
          updateEmptyListIndicatorVisibility()
          showSnackBar(R.string.preset_deleted)

          // maybe show in-app review dialog to the user
          ReviewFlowProvider.of(requireContext()).maybeAskForReview(requireActivity())
        }
      }
    }

    /**
     * cancels the wake-up timer if it was scheduled with the given [Preset.id].
     */
    private fun cancelWakeUpTimerIfScheduled(@Suppress("SameParameterValue") id: String) {
      WakeUpTimerManager.get(requireContext())?.also {
        if (id == it.presetID) {
          WakeUpTimerManager.cancel(requireContext())
        }
      }
    }

    private fun showSnackBar(@StringRes message: Int) {
      Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
        .setAction(R.string.dismiss) { }
        .show()
    }
  }
}

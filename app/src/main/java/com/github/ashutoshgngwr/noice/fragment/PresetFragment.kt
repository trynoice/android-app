package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.InAppReviewFlowManager
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.ShortcutHandlerActivity
import com.github.ashutoshgngwr.noice.WakeUpTimerManager
import com.github.ashutoshgngwr.noice.databinding.PresetListFragmentBinding
import com.github.ashutoshgngwr.noice.databinding.PresetListItemBinding
import com.github.ashutoshgngwr.noice.sound.Preset
import com.google.android.material.snackbar.Snackbar
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

class PresetFragment : Fragment() {

  companion object {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    const val PREF_SAVED_PRESETS_AS_HOME_SCREEN = "pref_saved_presets_as_homescreen"

    fun shouldDisplayAsHomeScreen(context: Context): Boolean {
      return PreferenceManager.getDefaultSharedPreferences(context)
        .getBoolean(PREF_SAVED_PRESETS_AS_HOME_SCREEN, false)
    }
  }

  private lateinit var binding: PresetListFragmentBinding

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
    binding.shouldDisplayAsHomeScreen.isChecked = shouldDisplayAsHomeScreen(requireContext())
    binding.shouldDisplayAsHomeScreen.setOnCheckedChangeListener { _, enabled ->
      PreferenceManager.getDefaultSharedPreferences(context).edit {
        putBoolean(PREF_SAVED_PRESETS_AS_HOME_SCREEN, enabled)
      }
    }

    dataSet = Preset.readAllFromUserPreferences(requireContext()).toMutableList()
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
  fun onPlayerManagerUpdate(event: MediaPlayerService.OnPlayerManagerUpdateEvent) {
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
          MediaPlayerService.playPreset(requireContext(), dataSet[adapterPosition].id)
        } else {
          MediaPlayerService.stopPlayback(requireContext())
        }
      }

      val onMenuItemClickListener = PopupMenu.OnMenuItemClickListener {
        when (it.itemId) {
          R.id.action_create_pinned_shortcut -> createPinnedShortcut()
          R.id.action_create_app_shortcut -> createAppShortcut()
          R.id.action_remove_app_shortcut -> removeAppShortcut()
          R.id.action_delete -> showDeletePresetConfirmation()
          R.id.action_rename -> showRenamePresetInput()
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

    private fun createPinnedShortcut() {
      if (!ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())) {
        Snackbar.make(requireView(), R.string.pinned_shortcuts_not_supported, Snackbar.LENGTH_LONG)
          .show()
        return
      }

      val info = buildShortcutInfo(UUID.randomUUID().toString())
      ShortcutManagerCompat.requestPinShortcut(requireContext(), info, null)
    }

    private fun createAppShortcut() {
      val list = ShortcutManagerCompat.getDynamicShortcuts(requireContext())
      val presetID = dataSet[adapterPosition].id
      list.add(buildShortcutInfo(presetID))

      var message = R.string.app_shortcut_creation_failed
      if (ShortcutManagerCompat.addDynamicShortcuts(requireContext(), list)) {
        message = R.string.app_shortcut_created
      }

      Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
    }

    private fun removeAppShortcut() {
      val presetID = dataSet[adapterPosition].id
      ShortcutManagerCompat.removeDynamicShortcuts(requireContext(), listOf(presetID))
      Snackbar.make(requireView(), R.string.app_shortcut_removed, Snackbar.LENGTH_LONG).show()
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
        val duplicateNameValidator = Preset.duplicateNameValidator(requireContext())
        title(R.string.rename)
        input(
          hintRes = R.string.name,
          preFillValue = dataSet[adapterPosition].name,
          validator = {
            when {
              it.isBlank() -> R.string.preset_name_cannot_be_empty
              dataSet[adapterPosition].name == it -> 0 // no error if the name didn't change
              duplicateNameValidator(it) -> R.string.preset_already_exists
              else -> 0
            }
          }
        )

        negativeButton(R.string.cancel)
        positiveButton(R.string.save) {
          dataSet[adapterPosition].name = getInputText()
          Preset.writeAllToUserPreferences(requireContext(), dataSet)
          adapter?.notifyItemChanged(adapterPosition)

          // maybe show in-app review dialog to the user
          InAppReviewFlowManager.maybeAskForReview(requireActivity())
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
          Preset.writeAllToUserPreferences(requireContext(), dataSet)
          // then stop playback if recently deleted preset was playing
          if (adapterPosition == activePresetPos) {
            MediaPlayerService.stopPlayback(requireContext())
          }

          if (adapterPosition < activePresetPos) {
            activePresetPos -= 1 // account for recent deletion
          }

          cancelWakeUpTimerIfScheduled(preset.id)
          ShortcutManagerCompat.removeDynamicShortcuts(requireContext(), listOf(preset.id))
          adapter?.notifyItemRemoved(adapterPosition)
          updateEmptyListIndicatorVisibility()
          Snackbar.make(requireView(), R.string.preset_deleted, Snackbar.LENGTH_LONG)
            .setAction(R.string.dismiss) { }
            .show()

          // maybe show in-app review dialog to the user
          InAppReviewFlowManager.maybeAskForReview(requireActivity())
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
  }
}

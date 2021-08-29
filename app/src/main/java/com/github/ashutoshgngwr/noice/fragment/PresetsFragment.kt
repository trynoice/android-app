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
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.WakeUpTimerManager
import com.github.ashutoshgngwr.noice.activity.ShortcutHandlerActivity
import com.github.ashutoshgngwr.noice.databinding.PresetsFragmentBinding
import com.github.ashutoshgngwr.noice.databinding.PresetsListItemBinding
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.google.android.material.snackbar.Snackbar
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

class PresetsFragment : Fragment() {

  private lateinit var binding: PresetsFragmentBinding
  private lateinit var presetRepository: PresetRepository
  private lateinit var analyticsProvider: AnalyticsProvider

  private var adapter: PresetListAdapter? = null
  private var activePresetPos = -1
  private var dataSet = mutableListOf<Preset>()

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = PresetsFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    presetRepository = PresetRepository.newInstance(requireContext())
    analyticsProvider = NoiceApplication.of(requireContext()).getAnalyticsProvider()
    dataSet = presetRepository.list().toMutableList()
    adapter = PresetListAdapter(requireContext())
    binding.list.also {
      it.adapter = adapter
      it.setHasFixedSize(true)
      it.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
    }

    EventBus.getDefault().register(this)
    updateEmptyListIndicatorVisibility()

    val params = bundleOf("items_count" to dataSet.size)
    analyticsProvider.setCurrentScreen("presets", PresetsFragment::class, params)
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
      return ViewHolder(PresetsListItemBinding.inflate(layoutInflater, parent, false))
    }

    override fun getItemCount(): Int {
      return dataSet.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      holder.binding.title.text = dataSet[position].name
      holder.binding.playButton.isChecked = position == activePresetPos
    }
  }

  inner class ViewHolder(val binding: PresetsListItemBinding) :
    RecyclerView.ViewHolder(binding.root) {

    private val reviewFlowProvider = NoiceApplication.of(requireContext()).getReviewFlowProvider()

    init {
      binding.playButton.setOnClickListener {
        if (bindingAdapterPosition != activePresetPos) {
          PlaybackController.playPreset(requireContext(), dataSet[bindingAdapterPosition].id)
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

        analyticsProvider.logEvent("preset_context_menu_open", bundleOf())
      }
    }

    private fun showShareIntentSender() {
      val uri = dataSet[bindingAdapterPosition].toUri().toString()
      ShareCompat.IntentBuilder(requireActivity())
        .setType("text/plain")
        .setChooserTitle(R.string.share)
        .setText(uri)
        .startChooser()

      analyticsProvider.logEvent("share_preset_uri", bundleOf("item_length" to uri.length))
    }

    private fun createPinnedShortcut() {
      if (!ShortcutManagerCompat.isRequestPinShortcutSupported(requireContext())) {
        showSnackBar(R.string.pinned_shortcuts_not_supported)
        return
      }

      val info = buildShortcutInfo(UUID.randomUUID().toString(), "pinned")
      val result = ShortcutManagerCompat.requestPinShortcut(requireContext(), info, null)
      if (!result) {
        showSnackBar(R.string.pinned_shortcut_creation_failed)
      } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        showSnackBar(R.string.pinned_shortcut_created)
      }

      val params = bundleOf("success" to result, "shortcut_type" to "pinned")
      analyticsProvider.logEvent("preset_shortcut_create", params)
    }

    private fun createAppShortcut() {
      val list = ShortcutManagerCompat.getDynamicShortcuts(requireContext())
      val presetID = dataSet[bindingAdapterPosition].id
      list.add(buildShortcutInfo(presetID, "app"))

      val result = ShortcutManagerCompat.addDynamicShortcuts(requireContext(), list)
      if (result) {
        showSnackBar(R.string.app_shortcut_created)
      } else {
        showSnackBar(R.string.app_shortcut_creation_failed)
      }

      val params = bundleOf("success" to result, "shortcut_type" to "app")
      analyticsProvider.logEvent("preset_shortcut_create", params)
    }

    private fun removeAppShortcut() {
      val presetID = dataSet[bindingAdapterPosition].id
      ShortcutManagerCompat.removeDynamicShortcuts(requireContext(), listOf(presetID))
      showSnackBar(R.string.app_shortcut_removed)
      analyticsProvider.logEvent("preset_shortcut_remove", bundleOf("shortcut_type" to "app"))
    }

    private fun hasAppShortcut(): Boolean {
      ShortcutManagerCompat.getDynamicShortcuts(requireContext()).forEach {
        if (it.id == dataSet[bindingAdapterPosition].id) {
          return true
        }
      }

      return false
    }

    private fun buildShortcutInfo(shortcutID: String, type: String): ShortcutInfoCompat {
      return with(ShortcutInfoCompat.Builder(requireContext(), shortcutID)) {
        setShortLabel(dataSet[bindingAdapterPosition].name)
        setIcon(IconCompat.createWithResource(requireContext(), R.mipmap.ic_preset_shortcut))
        setIntent(
          Intent(requireContext(), ShortcutHandlerActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(ShortcutHandlerActivity.EXTRA_SHORTCUT_ID, shortcutID)
            .putExtra(ShortcutHandlerActivity.EXTRA_SHORTCUT_TYPE, type)
            .putExtra(ShortcutHandlerActivity.EXTRA_PRESET_ID, dataSet[bindingAdapterPosition].id)
        )

        build()
      }
    }

    private fun showRenamePresetInput() {
      val params = bundleOf("success" to false)
      DialogFragment.show(childFragmentManager) {
        title(R.string.rename)
        input(
          hintRes = R.string.name,
          preFillValue = dataSet[bindingAdapterPosition].name,
          validator = {
            when {
              it.isBlank() -> R.string.preset_name_cannot_be_empty
              dataSet[bindingAdapterPosition].name == it -> 0 // no error if the name didn't change
              dataSet.any { p -> it == p.name } -> R.string.preset_already_exists
              else -> 0
            }
          }
        )

        negativeButton(R.string.cancel)
        positiveButton(R.string.save) {
          val name = getInputText()
          dataSet[bindingAdapterPosition].name = name
          presetRepository.update(dataSet[bindingAdapterPosition])
          adapter?.notifyItemChanged(bindingAdapterPosition)
          PlaybackController.requestUpdateEvent(requireContext())

          // maybe show in-app review dialog to the user
          reviewFlowProvider.maybeAskForReview(requireActivity())
          params.putBoolean("success", true)
          analyticsProvider.logEvent("preset_name", bundleOf("item_length" to name.length))
        }

        onDismiss { analyticsProvider.logEvent("preset_rename", params) }
      }
    }

    private fun showDeletePresetConfirmation() {
      val params = bundleOf("success" to false)
      DialogFragment.show(requireActivity().supportFragmentManager) {
        title(R.string.delete)
        message(R.string.preset_delete_confirmation, dataSet[bindingAdapterPosition].name)
        negativeButton(R.string.cancel)
        positiveButton(R.string.delete) {
          val preset = dataSet.removeAt(bindingAdapterPosition)
          presetRepository.delete(preset.id)
          // then stop playback if recently deleted preset was playing
          if (bindingAdapterPosition == activePresetPos) {
            PlaybackController.stop(requireContext())
          }

          if (bindingAdapterPosition < activePresetPos) {
            activePresetPos -= 1 // account for recent deletion
          }

          cancelWakeUpTimerIfScheduled(preset.id)
          ShortcutManagerCompat.removeDynamicShortcuts(requireContext(), listOf(preset.id))
          adapter?.notifyItemRemoved(bindingAdapterPosition)
          updateEmptyListIndicatorVisibility()
          showSnackBar(R.string.preset_deleted)

          params.putBoolean("success", true)
          // maybe show in-app review dialog to the user
          reviewFlowProvider.maybeAskForReview(requireActivity())
        }

        onDismiss { analyticsProvider.logEvent("preset_delete", params) }
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
      Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()
    }
  }
}

package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.sound.Playback
import com.github.ashutoshgngwr.noice.sound.PlaybackControlEvents
import com.google.android.material.snackbar.Snackbar
import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import kotlinx.android.synthetic.main.fragment_preset_list.view.*
import kotlinx.android.synthetic.main.layout_list_item__preset.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class PresetFragment : Fragment() {

  private var mRecyclerView: RecyclerView? = null
  private var dataSet = ArrayList<Preset>()
  private var activePreset: Preset? = null
  private var eventBus = EventBus.getDefault()
  private lateinit var adapter: PresetListAdapter

  private val mAdapterDataObserver = object : RecyclerView.AdapterDataObserver() {
    override fun onChanged() {
      if (adapter.itemCount > 0) {
        requireView().indicator_list_empty.visibility = View.GONE
      } else {
        requireView().indicator_list_empty.visibility = View.VISIBLE
      }
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.fragment_preset_list, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    dataSet.addAll(Preset.readAllFromUserPreferences(requireContext()))
    adapter = PresetListAdapter(requireContext()).apply {
      registerAdapterDataObserver(mAdapterDataObserver)
    }

    mRecyclerView = view.list_presets.also {
      it.setHasFixedSize(true)
      it.adapter = adapter
    }

    registerOnEventBus()
    mAdapterDataObserver.onChanged() // since observer is not called by adapter on initialization
  }

  override fun onDestroyView() {
    unregisterFromEventBus()
    adapter.unregisterAdapterDataObserver(mAdapterDataObserver)
    super.onDestroyView()
  }

  /*
    registerOnEventBus and unregisterFromEventBus are helper functions to avoid labelled
    expressions warning (lint warning in Detekt)
   */

  private fun registerOnEventBus() {
    eventBus.register(this)
  }

  private fun unregisterFromEventBus() {
    eventBus.unregister(this)
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onPlaybackUpdate(playbacks: HashMap<String, Playback>) {
    activePreset = Preset("", playbacks.values.toTypedArray())
    adapter.notifyDataSetChanged()
  }

  inner class PresetListAdapter(context: Context) : RecyclerView.Adapter<ViewHolder>() {

    private val layoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      return ViewHolder(layoutInflater.inflate(R.layout.layout_list_item__preset, parent, false))
    }

    override fun getItemCount(): Int {
      return dataSet.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      holder.itemView.title.text = dataSet[position].name
      holder.itemView.button_play.setImageResource(
        if (dataSet[position] == activePreset) {
          R.drawable.ic_action_stop
        } else {
          R.drawable.ic_action_play
        }
      )
    }
  }

  inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    init {
      itemView.button_play.setOnClickListener {
        // publishing StopPlaybackEvent will cause PlaybackManager to send back State update
        // This will call notifyDataSetChanged() on adapter causing adapter position to be
        // negative. assigning adapterPosition to a different variable isn't helping here.
        // I guess its safe to avoid receiving any events during following logic.
        // Unsubscribe temporarily. Lets hope this doesn't have any significant side-effects. :p
        unregisterFromEventBus()
        eventBus.post(PlaybackControlEvents.StopPlaybackEvent())
        if (dataSet[adapterPosition] == activePreset) {
          itemView.button_play.setImageResource(R.drawable.ic_action_play)
        } else {
          itemView.button_play.setImageResource(R.drawable.ic_action_stop)
          for (p in dataSet[adapterPosition].playbackStates) {
            eventBus.post(PlaybackControlEvents.StartPlaybackEvent(p.soundKey))
            eventBus.post(PlaybackControlEvents.UpdatePlaybackEvent(p))
          }
        }
        registerOnEventBus()
      }

      val onMenuItemClickListener = PopupMenu.OnMenuItemClickListener {
        when (it.itemId) {
          R.id.action_delete -> showDeletePresetConfirmation()
          R.id.action_rename -> showRenamePresetInput()
        }

        true
      }

      itemView.button_menu.setOnClickListener {
        PopupMenu(requireContext(), itemView.button_menu).let {
          it.inflate(R.menu.preset)
          it.setOnMenuItemClickListener(onMenuItemClickListener)
          it.show()
        }
      }
    }

    private fun showRenamePresetInput() {
      DialogFragment().show(requireActivity().supportFragmentManager) {
        title(R.string.rename)
        input(
          hintRes = R.string.name,
          preFillValue = dataSet[adapterPosition].name,
          errorRes = R.string.preset_name_cannot_be_empty
        )
        negativeButton(R.string.cancel)
        positiveButton(R.string.save) {
          dataSet[adapterPosition].name = getInputText()
          Preset.writeAllToUserPreferences(requireContext(), dataSet)
          adapter.notifyItemChanged(adapterPosition)
        }
      }
    }

    private fun showDeletePresetConfirmation() {
      DialogFragment().show(requireActivity().supportFragmentManager) {
        title(R.string.delete)
        message(R.string.preset_delete_confirmation, dataSet[adapterPosition].name)
        negativeButton(R.string.cancel)
        positiveButton(R.string.delete) {
          val preset = dataSet.removeAt(adapterPosition)
          Preset.writeAllToUserPreferences(requireContext(), dataSet)
          // then stop playback if recently deleted preset was playing
          if (preset == activePreset) {
            eventBus.post(PlaybackControlEvents.StopPlaybackEvent())
          }

          adapter.notifyDataSetChanged()
          Snackbar.make(requireView(), R.string.preset_deleted, Snackbar.LENGTH_LONG)
            .setAction(R.string.dismiss) { }
            .show()
        }
      }
    }
  }

  // curious about the weird serialized names? see https://github.com/ashutoshgngwr/noice/issues/110
  // and https://github.com/ashutoshgngwr/noice/pulls/117
  data class Preset(
    @Expose @SerializedName("a") var name: String,
    @Expose @SerializedName("b") val playbackStates: Array<Playback>
  ) {

    init {
      playbackStates.sortBy { T -> T.soundKey }
    }

    companion object {
      fun readAllFromUserPreferences(context: Context): ArrayList<Preset> {
        GsonBuilder()
          .excludeFieldsWithoutExposeAnnotation()
          .create()
          .also {
            return ArrayList(
              it.fromJson(
                PreferenceManager.getDefaultSharedPreferences(context).getString("presets", "[]"),
                Array<Preset>::class.java
              ).asList()
            )
          }
      }

      fun writeAllToUserPreferences(context: Context, presets: ArrayList<Preset>) {
        GsonBuilder()
          .excludeFieldsWithoutExposeAnnotation()
          .create()
          .also { gson ->
            PreferenceManager.getDefaultSharedPreferences(context).also { sharedPreferences ->
              sharedPreferences.edit()
                .putString("presets", gson.toJson(presets.toTypedArray()))
                .apply()
            }
          }
      }

      fun appendToUserPreferences(context: Context, preset: Preset) {
        readAllFromUserPreferences(context).also {
          it.add(preset)
          writeAllToUserPreferences(context, it)
        }
      }
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) {
        return true
      }

      if (javaClass != other?.javaClass) {
        return false
      }

      // name need not be equal. playbackStates should be
      other as Preset
      return playbackStates.contentEquals(other.playbackStates)
    }

    override fun hashCode(): Int {
      // auto-generated
      var result = name.hashCode()
      result = 31 * result + playbackStates.contentHashCode()
      return result
    }
  }
}

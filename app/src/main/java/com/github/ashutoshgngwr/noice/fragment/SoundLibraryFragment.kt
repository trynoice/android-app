package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.sound.Preset
import com.github.ashutoshgngwr.noice.sound.Sound
import com.github.ashutoshgngwr.noice.sound.player.Player
import com.github.ashutoshgngwr.noice.sound.player.PlayerManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_sound_list.view.*
import kotlinx.android.synthetic.main.layout_list_item__sound.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class SoundLibraryFragment : Fragment(R.layout.fragment_sound_list) {

  private var mRecyclerView: RecyclerView? = null
  private var mSavePresetButton: FloatingActionButton? = null
  private var adapter: SoundListAdapter? = null
  private var players = emptyMap<String, Player>()

  private val eventBus = EventBus.getDefault()
  private val dataSet = Sound.LIBRARY.values.toTypedArray()

  @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
  fun onPlayerManagerUpdate(event: MediaPlayerService.OnPlayerManagerUpdateEvent) {
    this.players = event.players
    var showSavePresetFAB: Boolean
    Preset.readAllFromUserPreferences(requireContext()).also {
      showSavePresetFAB = !it.contains(Preset.from("", players.values))
    }

    view?.post {
      adapter?.notifyDataSetChanged()
      if (mSavePresetButton != null) {
        if (showSavePresetFAB && event.state == PlayerManager.State.PLAYING) {
          mSavePresetButton?.show()
        } else {
          mSavePresetButton?.hide()
        }
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    adapter = SoundListAdapter(requireContext())
    mRecyclerView = view.list_sound.also {
      it.setHasFixedSize(true)
      it.adapter = adapter
    }

    mSavePresetButton = view.fab_save_preset
    requireNotNull(mSavePresetButton).setOnClickListener {
      DialogFragment().show(childFragmentManager) {
        val duplicateNameValidator = Preset.duplicateNameValidator(requireContext())
        title(R.string.save_preset)
        input(hintRes = R.string.name, validator = {
          when {
            it.isBlank() -> R.string.preset_name_cannot_be_empty
            duplicateNameValidator(it) -> R.string.preset_already_exists
            else -> 0
          }
        })

        negativeButton(R.string.cancel)
        positiveButton(R.string.save) {
          val preset = Preset.from(getInputText(), players.values)
          Preset.appendToUserPreferences(requireContext(), preset)
          mSavePresetButton?.hide()
          showPresetSavedMessage()
        }
      }
    }

    registerOnEventBus()
  }

  override fun onDestroyView() {
    unregisterFromEventBus()
    super.onDestroyView()
  }

  /*
    showPresetSavedMessage, registerOnEventBus and unregisterFromEventBus are helper functions
    to avoid labelled expressions warning (lint warning in Detekt)
   */

  private fun showPresetSavedMessage() {
    Snackbar.make(requireView(), R.string.preset_saved, Snackbar.LENGTH_LONG)
      .setAction(R.string.dismiss) { }
      .show()
  }

  private fun registerOnEventBus() {
    eventBus.register(this)
  }

  private fun unregisterFromEventBus() {
    eventBus.unregister(this)
  }

  inner class SoundListAdapter(private val context: Context) : RecyclerView.Adapter<ViewHolder>() {

    private val layoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      return ViewHolder(layoutInflater.inflate(R.layout.layout_list_item__sound, parent, false))
    }

    override fun getItemCount(): Int {
      return dataSet.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      val sound = dataSet[position]
      val isPlaying = players.containsKey(sound.key)
      holder.itemView.title.text = context.getString(sound.titleResId)
      holder.itemView.seekbar_volume.isEnabled = isPlaying
      holder.itemView.seekbar_time_period.isEnabled = isPlaying
      holder.itemView.button_play.isChecked = isPlaying
      if (isPlaying) {
        requireNotNull(players[sound.key]).also {
          holder.itemView.seekbar_volume.progress = it.volume
          holder.itemView.seekbar_time_period.progress = it.timePeriod - Player.MIN_TIME_PERIOD
        }
      } else {
        holder.itemView.seekbar_volume.progress = Player.DEFAULT_VOLUME
        holder.itemView.seekbar_time_period.progress =
          Player.DEFAULT_TIME_PERIOD - Player.MIN_TIME_PERIOD
      }

      holder.itemView.layout_time_period.visibility = if (sound.isLooping) {
        View.GONE
      } else {
        View.VISIBLE
      }
    }
  }

  inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

    init {
      // set listeners in holders to avoid object recreation on view recycle
      val seekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {

        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
          if (!fromUser) {
            return
          }

          val player = players[dataSet[adapterPosition].key] ?: return
          when (seekBar.id) {
            R.id.seekbar_volume -> {
              player.setVolume(progress)
            }
            R.id.seekbar_time_period -> {
              player.timePeriod = Player.MIN_TIME_PERIOD + progress
            }
          }
        }

        // unsubscribe from events during the seek action or it will cause adapter to refresh during
        // the action causing adapterPosition to become -1 (POSITION_NONE). On resubscribing,
        // this will also cause an update (#onPlayerManagerUpdate) since those events are sticky.
        override fun onStartTrackingTouch(seekBar: SeekBar?) = unregisterFromEventBus()
        override fun onStopTrackingTouch(seekBar: SeekBar?) = registerOnEventBus()
      }

      view.seekbar_volume.max = Player.MAX_VOLUME
      view.seekbar_volume.setOnSeekBarChangeListener(seekBarChangeListener)
      view.seekbar_time_period.max = Player.MAX_TIME_PERIOD - Player.MIN_TIME_PERIOD
      view.seekbar_time_period.setOnSeekBarChangeListener(seekBarChangeListener)
      view.button_play.setOnClickListener {
        val sound = dataSet.getOrNull(adapterPosition) ?: return@setOnClickListener
        if (players.containsKey(sound.key)) {
          eventBus.post(MediaPlayerService.StopPlayerEvent(sound.key))
        } else {
          eventBus.post(MediaPlayerService.StartPlayerEvent(sound.key))
        }
      }
    }
  }
}

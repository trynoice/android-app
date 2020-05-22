package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.sound.Playback
import com.github.ashutoshgngwr.noice.sound.PlaybackControlEvents
import com.github.ashutoshgngwr.noice.sound.Sound
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_sound_list.view.*
import kotlinx.android.synthetic.main.layout_list_item__sound.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class SoundLibraryFragment : Fragment() {

  private var mRecyclerView: RecyclerView? = null
  private var mSavePresetButton: FloatingActionButton? = null
  private var eventBus = EventBus.getDefault()
  private var playbacks = emptyMap<String, Playback>()
  private lateinit var adapter: SoundListAdapter

  private val dataSet = Sound.LIBRARY.values.toTypedArray()

  @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
  fun onPlaybackUpdate(playbacks: HashMap<String, Playback>) {
    this.playbacks = playbacks
    var showSavePresetFAB: Boolean
    PresetFragment.Preset.readAllFromUserPreferences(requireContext()).also {
      showSavePresetFAB = !it.contains(PresetFragment.Preset("", playbacks.values.toTypedArray()))
    }

    view?.post {
      adapter.notifyDataSetChanged()
      if (showSavePresetFAB && playbacks.isNotEmpty()) {
        mSavePresetButton?.show()
      } else {
        mSavePresetButton?.hide()
      }
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.fragment_sound_list, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    adapter = SoundListAdapter(requireContext())
    mRecyclerView = view.list_sound.also {
      it.setHasFixedSize(true)
      it.adapter = adapter
    }

    mSavePresetButton = view.fab_save_preset
    requireNotNull(mSavePresetButton).setOnClickListener {
      DialogFragment().show(requireActivity().supportFragmentManager) {
        title(R.string.save_preset)
        input(hintRes = R.string.name, errorRes = R.string.preset_name_cannot_be_empty)
        negativeButton(R.string.cancel)
        positiveButton(R.string.save) {
          val preset = PresetFragment.Preset(getInputText(), playbacks.values.toTypedArray())
          PresetFragment.Preset.appendToUserPreferences(requireContext(), preset)
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
      holder.itemView.title.text = context.getString(sound.titleResId)
      if (playbacks.containsKey(sound.key)) {
        val playback = requireNotNull(playbacks[sound.key])
        holder.itemView.seekbar_volume.progress = playback.volume
        holder.itemView.seekbar_time_period.progress = playback.timePeriod
        holder.itemView.seekbar_volume.isEnabled = true
        holder.itemView.seekbar_time_period.isEnabled = true
        holder.itemView.button_play.setImageResource(R.drawable.ic_action_stop)
      } else {
        holder.itemView.seekbar_volume.progress = Playback.DEFAULT_VOLUME
        holder.itemView.seekbar_time_period.progress = Playback.DEFAULT_TIME_PERIOD
        holder.itemView.seekbar_volume.isEnabled = false
        holder.itemView.seekbar_time_period.isEnabled = false
        holder.itemView.button_play.setImageResource(R.drawable.ic_action_play)
      }

      holder.itemView.layout_time_period.visibility = (if (sound.isLoopable) {
        View.GONE
      } else {
        View.VISIBLE
      })
    }
  }

  inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

    init {
      // set listeners in holders to avoid object recreation on view recycle
      val seekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {

        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          if (!fromUser) {
            return
          }

          val playback = playbacks[dataSet[adapterPosition].key] ?: return
          when (requireNotNull(seekBar).id) {
            R.id.seekbar_volume -> {
              playback.setVolume(progress)
            }

            R.id.seekbar_time_period -> {
              // manually ensure minimum time period to be 1 since ProgressBar#min was introduced
              // in API 26. Our min API version is 21.
              playback.timePeriod = maxOf(1, progress)
            }
          }

          // publish update event
          eventBus.post(PlaybackControlEvents.UpdatePlaybackEvent(playback))
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
      }

      view.seekbar_volume.max = Playback.MAX_VOLUME
      view.seekbar_volume.setOnSeekBarChangeListener(seekBarChangeListener)
      view.seekbar_time_period.max = Playback.MAX_TIME_PERIOD
      view.seekbar_time_period.setOnSeekBarChangeListener(seekBarChangeListener)
      view.button_play.setOnClickListener {
        val sound = dataSet.getOrNull(adapterPosition) ?: return@setOnClickListener
        if (playbacks.containsKey(sound.key)) {
          eventBus.post(PlaybackControlEvents.StopPlaybackEvent(sound.key))
        } else {
          eventBus.post(PlaybackControlEvents.StartPlaybackEvent(sound.key))
        }
      }
    }
  }
}

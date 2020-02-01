package com.github.ashutoshgngwr.noice.fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
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

  companion object {
    const val RC_SAVE_PRESET_DIALOG = 0x922
  }

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  var mRecyclerView: RecyclerView? = null
  private lateinit var mSavePresetButton: FloatingActionButton
  private var eventBus = EventBus.getDefault()
  private var playbacks = emptyMap<String, Playback>()

  @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
  fun onPlaybackUpdate(playbacks: HashMap<String, Playback>) {
    this.playbacks = playbacks
    var showSavePresetFAB: Boolean
    PresetFragment.Preset.readAllFromUserPreferences(requireContext()).also {
      showSavePresetFAB = !it.contains(PresetFragment.Preset("", playbacks.values.toTypedArray()))
    }

    requireView().post {
      requireNotNull(requireNotNull(mRecyclerView).adapter).notifyDataSetChanged()
      if (showSavePresetFAB && playbacks.isNotEmpty()) {
        mSavePresetButton.show()
      } else {
        mSavePresetButton.hide()
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
    mRecyclerView = view.list_sound.apply {
      setHasFixedSize(true)
      adapter = SoundListAdapter(requireContext())
    }

    mSavePresetButton = view.fab_save_preset
    mSavePresetButton.setOnClickListener {
      SavePresetDialogFragment::class.java.newInstance().run {
        preset = PresetFragment.Preset("", playbacks.values.toTypedArray())
        setTargetFragment(this@SoundLibraryFragment, RC_SAVE_PRESET_DIALOG)
        show(
          this@SoundLibraryFragment.requireActivity().supportFragmentManager,
          this.javaClass.simpleName
        )
      }
    }

    eventBus.register(this)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == RC_SAVE_PRESET_DIALOG && resultCode == Activity.RESULT_OK) {
      showMessage(R.string.preset_saved)
      mSavePresetButton.hide()
    }
  }

  override fun onDestroyView() {
    eventBus.unregister(this)
    super.onDestroyView()
  }

  @Suppress("DEPRECATION")
  fun showMessage(@StringRes messageId: Int) {
    Snackbar.make(requireView(), messageId, Snackbar.LENGTH_LONG)
      .setAction(R.string.dismiss) { }
      .setBackgroundTint(resources.getColor(R.color.colorPrimary))
      .show()
  }

  inner class SoundListAdapter(private val context: Context) :
    RecyclerView.Adapter<SoundListAdapter.ViewHolder>() {

    private val layoutInflater = LayoutInflater.from(context)
    private val dataSet = Sound.LIBRARY.values.toTypedArray()

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
        holder.itemView.button_play.setImageResource(
          if (playback.isPlaying) {
            R.drawable.ic_action_stop
          } else {
            R.drawable.ic_action_stop
          }
        )
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

          override fun onStartTrackingTouch(seekBar: SeekBar?) {}
          override fun onStopTrackingTouch(seekBar: SeekBar?) {}

        }

        view.seekbar_volume.max = Playback.MAX_VOLUME
        view.seekbar_volume.setOnSeekBarChangeListener(seekBarChangeListener)
        view.seekbar_time_period.max = Playback.MAX_TIME_PERIOD
        view.seekbar_time_period.setOnSeekBarChangeListener(seekBarChangeListener)

        view.button_play.setOnClickListener {
          // return if mSoundManager is null
          val soundKey = dataSet[adapterPosition].key

          if (playbacks.containsKey(soundKey)) {
            eventBus.post(PlaybackControlEvents.StopPlaybackEvent(soundKey))
          } else {
            eventBus.post(PlaybackControlEvents.StartPlaybackEvent(soundKey))
          }
        }
      }
    }
  }
}

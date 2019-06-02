package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import android.os.Bundle
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.util.set
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.MainActivity
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment.Sound.Companion.LIBRARY
import kotlinx.android.synthetic.main.layout_list_item__sound.view.*

class SoundLibraryFragment : Fragment() {

  lateinit var recyclerView: RecyclerView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    recyclerView = RecyclerView(context!!)
    recyclerView.setHasFixedSize(true)
    recyclerView.layoutManager = LinearLayoutManager(context)
    recyclerView.adapter = ListAdapter(context!!)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return recyclerView
  }

  inner class ListAdapter(private val context: Context) : RecyclerView.Adapter<ListAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

      init {
        // set listeners in holders to avoid object recreation on view recycle
        val seekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {

          override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            val soundResId = LIBRARY.valueAt(adapterPosition).resId
            val soundManager = (activity as MainActivity).mSoundManager ?: return
            when (seekBar?.id) {
              R.id.seekbar_volume -> {
                soundManager.setVolume(soundResId, progress)
              }

              R.id.seekbar_time_period -> {
                soundManager.setTimePeriod(soundResId, progress)
              }
            }
          }

          override fun onStartTrackingTouch(seekBar: SeekBar?) {}
          override fun onStopTrackingTouch(seekBar: SeekBar?) {}

        }

        view.seekbar_volume.setOnSeekBarChangeListener(seekBarChangeListener)
        view.seekbar_time_period.setOnSeekBarChangeListener(seekBarChangeListener)

        view.button_play.setOnClickListener {
          val soundManager = (activity as MainActivity).mSoundManager ?: return@setOnClickListener
          val soundResId = LIBRARY.valueAt(adapterPosition).resId

          if (soundManager.isPlaying(soundResId)) {
            soundManager.stop(soundResId)
            view.button_play.setImageResource(R.drawable.ic_action_play)
          } else {
            soundManager.play(soundResId)
            view.button_play.setImageResource(R.drawable.ic_action_stop)
          }
        }
      }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      return ViewHolder(
        LayoutInflater
          .from(context)
          .inflate(R.layout.layout_list_item__sound, parent, false)
      )
    }

    override fun getItemCount(): Int {
      return LIBRARY.size()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      val soundManager = (activity as MainActivity).mSoundManager
      val sound = LIBRARY.valueAt(position)

      holder.itemView.title.text = context.getString(sound.titleResId)

      if (soundManager != null) {
        holder.itemView.seekbar_volume.progress = soundManager.getVolume(sound.resId)

        if (soundManager.isPlaying(sound.resId)) {
          holder.itemView.button_play.setImageResource(R.drawable.ic_action_stop)
        } else {
          holder.itemView.button_play.setImageResource(R.drawable.ic_action_play)
        }
      } else {
        holder.itemView.seekbar_volume.progress = 4
      }

      if (sound.isLoopable) {
        holder.itemView.layout_time_period.visibility = View.GONE
      } else {
        holder.itemView.layout_time_period.visibility = View.VISIBLE

        if (soundManager != null) {
          holder.itemView.seekbar_time_period.progress = soundManager.getTimePeriod(sound.resId)
        } else {
          holder.itemView.seekbar_time_period.progress = 60
        }
      }
    }
  }

  class Sound private constructor(val resId: Int, val titleResId: Int) {

    var isLoopable = true

    constructor(resId: Int, titleResId: Int, isLoopable: Boolean) : this(resId, titleResId) {
      this.isLoopable = isLoopable
    }

    companion object {

      val LIBRARY: SparseArray<Sound> = SparseArray()

      init {
        LIBRARY[R.raw.leaves_1] = Sound(R.raw.leaves_1, R.string.sound_leaves_1)
        LIBRARY[R.raw.leaves_2] = Sound(R.raw.leaves_2, R.string.sound_leaves_2)
        LIBRARY[R.raw.rain_1] = Sound(R.raw.rain_1, R.string.sound_rain_1)
        LIBRARY[R.raw.rain_2] = Sound(R.raw.rain_2, R.string.sound_rain_2)
        LIBRARY[R.raw.rain_3] = Sound(R.raw.rain_3, R.string.sound_rain_3)
        LIBRARY[R.raw.thunder_1] = Sound(R.raw.thunder_1, R.string.sound_thunder_1, false)
        LIBRARY[R.raw.thunder_2] = Sound(R.raw.thunder_2, R.string.sound_thunder_2, false)
        LIBRARY[R.raw.thunder_3] = Sound(R.raw.thunder_3, R.string.sound_thunder_3, false)
        LIBRARY[R.raw.wind_1] = Sound(R.raw.wind_1, R.string.sound_wind_1)
        LIBRARY[R.raw.wind_2] = Sound(R.raw.wind_2, R.string.sound_wind_2)
      }
    }
  }
}

package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.util.size
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.sound.Sound.Companion.SOUND_LIBRARY
import kotlinx.android.synthetic.main.layout_list_item__sound.view.*

class HomeFragment : Fragment() {

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val recyclerView = RecyclerView(context!!)
    recyclerView.setHasFixedSize(true)
    recyclerView.layoutManager = LinearLayoutManager(context)
    recyclerView.adapter = ListAdapter(context!!)
    return recyclerView
  }

  class ListAdapter(private val context: Context) : RecyclerView.Adapter<ListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

      init {
        // set listeners in holders to avoid object recreation on view recycle
        val seekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {

          override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            when (seekBar?.id) {
              R.id.seekbar_volume ->
                SOUND_LIBRARY
                  .valueAt(adapterPosition)
                  .volume = progress

              R.id.seekbar_time_period ->
                SOUND_LIBRARY
                  .valueAt(adapterPosition)
                  .timePeriod = progress
            }

            // TODO send updates
          }

          override fun onStartTrackingTouch(seekBar: SeekBar?) {}
          override fun onStopTrackingTouch(seekBar: SeekBar?) {}

        }

        view.seekbar_volume.setOnSeekBarChangeListener(seekBarChangeListener)
        view.seekbar_time_period.setOnSeekBarChangeListener(seekBarChangeListener)

        view.button_play.setOnClickListener {
          // TODO send update before updating icon so that SOUND_LIBRARY[id] is updated
          if (
            SOUND_LIBRARY
              .valueAt(adapterPosition)
              .isPlaying
          ) {
            view.button_play.setImageResource(R.drawable.ic_action_stop)
          } else {
            view.button_play.setImageResource(R.drawable.ic_action_play)
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
      return SOUND_LIBRARY.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      val sound = SOUND_LIBRARY.valueAt(position)

      holder.itemView.title.text = context.getString(sound.titleResId)
      holder.itemView.seekbar_volume.progress = sound.volume

      if (sound.isLoopable) {
        holder.itemView.layout_time_period.visibility = View.GONE
      } else {
        holder.itemView.layout_time_period.visibility = View.VISIBLE
        holder.itemView.seekbar_time_period.progress = sound.timePeriod
      }
    }
  }
}

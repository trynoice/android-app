package com.github.ashutoshgngwr.noice.fragment

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.util.set
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.SoundManager
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment.Sound.Companion.LIBRARY
import kotlinx.android.synthetic.main.layout_list_item__sound.view.*

class SoundLibraryFragment : Fragment(), SoundManager.OnPlaybackStateChangeListener {

  companion object {
    const val TAG: String = "SoundLibraryFragment"
  }

  private var mSoundManager: SoundManager? = null

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  var mRecyclerView: RecyclerView? = null

  private val mServiceConnection = object : ServiceConnection {
    override fun onServiceDisconnected(name: ComponentName?) {
      Log.d(TAG, "MediaPlayerService disconnected")
      mSoundManager?.removeOnPlaybackStateChangeListener(this@SoundLibraryFragment)
      mSoundManager = null
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      service ?: return

      Log.d(TAG, "MediaPlayerService connected")
      mSoundManager = (service as MediaPlayerService.PlaybackBinder).getSoundManager()
      mSoundManager?.addOnPlaybackStateChangeListener(this@SoundLibraryFragment)

      // once service is connected, update playback state in UI
      mRecyclerView?.adapter?.notifyDataSetChanged()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    context?.bindService(
      Intent(context, MediaPlayerService::class.java),
      mServiceConnection,
      Context.BIND_AUTO_CREATE
    )
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    if (mRecyclerView == null) {
      mRecyclerView = RecyclerView(context!!)
      mRecyclerView!!.setHasFixedSize(true)
      mRecyclerView!!.layoutManager = LinearLayoutManager(context)
      mRecyclerView!!.adapter = ListAdapter(context!!)
    }
    return mRecyclerView
  }

  override fun onDestroy() {
    context?.unbindService(mServiceConnection)

    // manually call onServiceDisconnected because framework does not
    // call it when service is intentionally unbound.
    mServiceConnection.onServiceDisconnected(null)
    super.onDestroy()
  }

  override fun onPlaybackStateChanged() {
    Log.d(TAG, "Playback state changed. Refreshing UI...")
    mRecyclerView?.adapter?.notifyDataSetChanged()

    // bring service to foreground if not done already
    ContextCompat.startForegroundService(
      context!!,
      Intent(context, MediaPlayerService::class.java)
    )
  }

  inner class ListAdapter(private val context: Context) : RecyclerView.Adapter<ListAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

      init {
        // set listeners in holders to avoid object recreation on view recycle
        val seekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {

          override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            // return if mSoundManager is null
            mSoundManager ?: return

            val soundResId = LIBRARY.valueAt(adapterPosition).resId
            when (seekBar?.id) {
              R.id.seekbar_volume -> {
                mSoundManager?.setVolume(soundResId, progress)
              }

              R.id.seekbar_time_period -> {
                mSoundManager?.setTimePeriod(soundResId, progress)
              }
            }
          }

          override fun onStartTrackingTouch(seekBar: SeekBar?) {}
          override fun onStopTrackingTouch(seekBar: SeekBar?) {}

        }

        view.seekbar_volume.setOnSeekBarChangeListener(seekBarChangeListener)
        view.seekbar_time_period.setOnSeekBarChangeListener(seekBarChangeListener)

        view.button_play.setOnClickListener {
          // return if mSoundManager is null
          mSoundManager ?: return@setOnClickListener

          val soundResId = LIBRARY.valueAt(adapterPosition).resId

          if (mSoundManager!!.isPlaying(soundResId)) {
            mSoundManager?.stop(soundResId)
            view.button_play.setImageResource(R.drawable.ic_action_play)
          } else {
            mSoundManager?.play(soundResId)
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
      val sound = LIBRARY.valueAt(position)

      holder.itemView.title.text = context.getString(sound.titleResId)

      if (mSoundManager != null) {
        holder.itemView.seekbar_volume.progress = mSoundManager!!.getVolume(sound.resId)

        if (mSoundManager!!.isPlaying(sound.resId)) {
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

        if (mSoundManager != null) {
          holder.itemView.seekbar_time_period.progress = mSoundManager!!.getTimePeriod(sound.resId)
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
        LIBRARY[R.raw.birds] = Sound(R.raw.birds, R.string.birds, false)
        LIBRARY[R.raw.bonfire] = Sound(R.raw.bonfire, R.string.bonfire)
        LIBRARY[R.raw.coffee_shop] = Sound(R.raw.coffee_shop, R.string.coffee_shop)
        LIBRARY[R.raw.distant_thunder] = Sound(R.raw.distant_thunder, R.string.distant_thunder, false)
        LIBRARY[R.raw.heavy_rain] = Sound(R.raw.heavy_rain, R.string.heavy_rain)
        LIBRARY[R.raw.light_rain] = Sound(R.raw.light_rain, R.string.light_rain)
        LIBRARY[R.raw.moderate_rain] = Sound(R.raw.moderate_rain, R.string.moderate_rain)
        LIBRARY[R.raw.moving_train] = Sound(R.raw.moving_train, R.string.moving_train)
        LIBRARY[R.raw.night] = Sound(R.raw.night, R.string.night)
        LIBRARY[R.raw.rolling_thunder] = Sound(R.raw.rolling_thunder, R.string.rolling_thunder, false)
        LIBRARY[R.raw.seaside] = Sound(R.raw.seaside, R.string.seaside)
        LIBRARY[R.raw.soft_wind] = Sound(R.raw.soft_wind, R.string.soft_wind)
        LIBRARY[R.raw.thunder_crack] = Sound(R.raw.thunder_crack, R.string.thunder_crack, false)
        LIBRARY[R.raw.train_horn] = Sound(R.raw.train_horn, R.string.train_horn, false)
        LIBRARY[R.raw.water_stream] = Sound(R.raw.water_stream, R.string.water_stream)
        LIBRARY[R.raw.wind_chimes_of_shells] = Sound(R.raw.wind_chimes_of_shells, R.string.wind_in_chimes_of_shells)
        LIBRARY[R.raw.wind_in_palm_trees] = Sound(R.raw.wind_in_palm_trees, R.string.wind_in_palm_trees)
      }
    }
  }
}

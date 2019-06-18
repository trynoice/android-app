package com.github.ashutoshgngwr.noice.fragment

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.SoundManager
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment.Sound.Companion.LIBRARY
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_sound_list.view.*
import kotlinx.android.synthetic.main.layout_list_item__sound.view.*

class SoundLibraryFragment : Fragment(), SoundManager.OnPlaybackStateChangeListener {

  companion object {
    const val TAG: String = "SoundLibraryFragment"
    const val RC_SAVE_PRESET_DIALOG = 0x922
  }

  private var mSoundManager: SoundManager? = null

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  lateinit var mRecyclerView: RecyclerView

  private lateinit var mSavePresetButton: FloatingActionButton

  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  val mServiceConnection = object : ServiceConnection {
    override fun onServiceDisconnected(name: ComponentName?) {
      Log.d(TAG, "MediaPlayerService disconnected")
      mSoundManager?.removeOnPlaybackStateChangeListener(this@SoundLibraryFragment)
      mSoundManager = null
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      service ?: return

      Log.d(TAG, "MediaPlayerService connected")
      mSoundManager = (service as MediaPlayerService.PlaybackBinder).getSoundManager()
      mSoundManager!!.addOnPlaybackStateChangeListener(this@SoundLibraryFragment)

      // once service is connected, update playback state in UI
      mRecyclerView.adapter?.notifyDataSetChanged()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    context!!.bindService(
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
    return inflater.inflate(R.layout.fragment_sound_list, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    mRecyclerView = view.list_sound
    mRecyclerView.setHasFixedSize(true)
    mRecyclerView.adapter = SoundListAdapter(context!!)
    mSavePresetButton = view.fab_save_preset
    mSavePresetButton.setOnClickListener {
      SavePresetDialogFragment::class.java.newInstance().run {
        preset = mSoundManager?.getCurrentPreset()!!
        setTargetFragment(this@SoundLibraryFragment, RC_SAVE_PRESET_DIALOG)
        show(this@SoundLibraryFragment.requireFragmentManager(), this.javaClass.simpleName)
      }
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == RC_SAVE_PRESET_DIALOG && resultCode == Activity.RESULT_OK) {
      showMessage(R.string.preset_saved)
      mSavePresetButton.hide()
    }
  }

  override fun onDestroy() {
    context?.unbindService(mServiceConnection)

    // manually call onServiceDisconnected because framework does not
    // call it when service is intentionally unbound.
    mServiceConnection.onServiceDisconnected(null)
    super.onDestroy()
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    if (playbackState == SoundManager.OnPlaybackStateChangeListener.STATE_PLAYBACK_STARTED) {
      Log.d(TAG, "Playback started! Bring media player service to foreground...")
      ContextCompat.startForegroundService(
        context!!,
        Intent(context, MediaPlayerService::class.java)
      )
    }

    // refresh sound list
    when (playbackState) {
      SoundManager.OnPlaybackStateChangeListener.STATE_PLAYBACK_STARTED,
      SoundManager.OnPlaybackStateChangeListener.STATE_PLAYBACK_PAUSED,
      SoundManager.OnPlaybackStateChangeListener.STATE_PLAYBACK_RESUMED,
      SoundManager.OnPlaybackStateChangeListener.STATE_PLAYBACK_STOPPED -> {
        Log.d(TAG, "Playback state changed. Refreshing sound list...")
        mRecyclerView.adapter?.notifyDataSetChanged()
      }
    }

    // hide or show save preset button based on current preset
    when (playbackState) {
      SoundManager.OnPlaybackStateChangeListener.STATE_PLAYBACK_STARTED,
      SoundManager.OnPlaybackStateChangeListener.STATE_PLAYBACK_RESUMED,
      SoundManager.OnPlaybackStateChangeListener.STATE_PLAYBACK_UPDATED -> {
        Log.d(TAG, "Current playback preset updated!")
        val preset = mSoundManager?.getCurrentPreset()
        if (preset == null || PresetFragment.Preset.readAllFromUserPreferences(context!!).contains(preset)) {
          mSavePresetButton.hide()
        } else {
          mSavePresetButton.show()
        }
      }
      else -> {
        Log.d(TAG, "Playback is either paused or stopped! Hide save preset button...")
        mSavePresetButton.hide()
      }
    }
  }

  @Suppress("DEPRECATION")
  fun showMessage(@StringRes messageId: Int) {
    Snackbar.make(requireView(), messageId, Snackbar.LENGTH_LONG)
      .setAction(R.string.dismiss) { }
      .setBackgroundTint(resources.getColor(R.color.colorPrimary))
      .show()
  }

  inner class SoundListAdapter(private val context: Context) : RecyclerView.Adapter<SoundListAdapter.ViewHolder>() {

    private val layoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      return ViewHolder(layoutInflater.inflate(R.layout.layout_list_item__sound, parent, false))
    }

    override fun getItemCount(): Int {
      return LIBRARY.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      val sound = LIBRARY[position]

      holder.itemView.title.text = context.getString(sound.titleResId)
      holder.itemView.seekbar_volume.progress = 4
      holder.itemView.seekbar_time_period.progress = 60

      val soundManager = mSoundManager ?: return
      holder.itemView.seekbar_volume.progress = soundManager.getVolume(sound.key)
      if (soundManager.isPlaying(sound.key)) {
        holder.itemView.button_play.setImageResource(R.drawable.ic_action_stop)
      } else {
        holder.itemView.button_play.setImageResource(R.drawable.ic_action_play)
      }

      if (sound.isLoopable) {
        holder.itemView.layout_time_period.visibility = View.GONE
      } else {
        holder.itemView.layout_time_period.visibility = View.VISIBLE
        holder.itemView.seekbar_time_period.progress = mSoundManager!!.getTimePeriod(sound.key)
      }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

      init {
        // set listeners in holders to avoid object recreation on view recycle
        val seekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {

          override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            // return if mSoundManager is null
            val soundManager = mSoundManager ?: return
            if (!fromUser) {
              return
            }

            val soundKey = LIBRARY[adapterPosition].key
            when (seekBar?.id) {
              R.id.seekbar_volume -> {
                soundManager.setVolume(soundKey, progress)
              }

              R.id.seekbar_time_period -> {
                soundManager.setTimePeriod(soundKey, progress)
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
          val soundManager = mSoundManager ?: return@setOnClickListener

          val soundKey = LIBRARY[adapterPosition].key

          if (soundManager.isPlaying(soundKey)) {
            soundManager.stop(soundKey)
            view.button_play.setImageResource(R.drawable.ic_action_play)
          } else {
            soundManager.play(soundKey)
            view.button_play.setImageResource(R.drawable.ic_action_stop)
            // if playback is paused, notify user that sound will be played when playback is resumed.
            if (soundManager.isPaused()) {
              showMessage(R.string.playback_is_paused)
            }
          }
        }
      }
    }
  }

  class Sound private constructor(@RawRes val resId: Int, @StringRes val titleResId: Int, val key: String) {

    var isLoopable = true

    constructor(resId: Int, titleResId: Int, isLoopable: Boolean, key: String) : this(resId, titleResId, key) {
      this.isLoopable = isLoopable
    }

    companion object {

      val LIBRARY = arrayOf(
        Sound(R.raw.birds, R.string.birds, "birds"),
        Sound(R.raw.bonfire, R.string.bonfire, "bonfire"),
        Sound(R.raw.coffee_shop, R.string.coffee_shop, "coffee_shop"),
        Sound(R.raw.distant_thunder, R.string.distant_thunder, false, "distant_thunder"),
        Sound(R.raw.heavy_rain, R.string.heavy_rain, "heavy_rain"),
        Sound(R.raw.light_rain, R.string.light_rain, "light_rain"),
        Sound(R.raw.moderate_rain, R.string.moderate_rain, "moderate_rain"),
        Sound(R.raw.morning_in_a_village, R.string.morning_in_a_village, "morning_in_a_village"),
        Sound(R.raw.moving_train, R.string.moving_train, "moving_train"),
        Sound(R.raw.night, R.string.night, "night"),
        Sound(R.raw.rolling_thunder, R.string.rolling_thunder, false, "rolling_thunder"),
        Sound(R.raw.seaside, R.string.seaside, "seaside"),
        Sound(R.raw.soft_wind, R.string.soft_wind, "soft_wind"),
        Sound(R.raw.thunder_crack, R.string.thunder_crack, false, "thunder_crack"),
        Sound(R.raw.train_horn, R.string.train_horn, false, "train_horn"),
        Sound(R.raw.water_stream, R.string.water_stream, "water_stream"),
        Sound(R.raw.wind_chimes_of_shells, R.string.wind_in_chimes_of_shells, "wind_in_chimes_of_shells"),
        Sound(R.raw.wind_in_palm_trees, R.string.wind_in_palm_trees, "wind_in_palm_trees")
      )
    }
  }
}

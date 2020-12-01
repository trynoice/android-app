package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.InAppReviewFlowManager
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.DialogFragmentRandomPresetBinding
import com.github.ashutoshgngwr.noice.databinding.SoundGroupListItemBinding
import com.github.ashutoshgngwr.noice.databinding.SoundLibraryFragmentBinding
import com.github.ashutoshgngwr.noice.databinding.SoundListItemBinding
import com.github.ashutoshgngwr.noice.sound.Preset
import com.github.ashutoshgngwr.noice.sound.Sound
import com.github.ashutoshgngwr.noice.sound.player.Player
import com.github.ashutoshgngwr.noice.sound.player.PlayerManager
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import kotlin.random.Random
import kotlin.random.nextInt

class SoundLibraryFragment : Fragment() {

  companion object {
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val RANGE_INTENSITY_LIGHT = 2 until 5

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val RANGE_INTENSITY_DENSE = 3 until 8

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val RANGE_INTENSITY_ANY = 2 until 8
  }

  private lateinit var binding: SoundLibraryFragmentBinding

  private var adapter: SoundListAdapter? = null
  private var players = emptyMap<String, Player>()

  private val eventBus = EventBus.getDefault()

  private val dataSet by lazy {
    arrayListOf<SoundListItem>().also { list ->
      var lastDisplayGroupResID = -1
      val sounds = Sound.LIBRARY.toSortedMap(
        compareBy(
          { getString(Sound.get(it).displayGroupResID) },
          { getString(Sound.get(it).titleResId) }
        )
      )

      sounds.forEach {
        if (lastDisplayGroupResID != it.value.displayGroupResID) {
          lastDisplayGroupResID = it.value.displayGroupResID
          list.add(
            SoundListItem(
              R.layout.sound_group_list_item, getString(lastDisplayGroupResID)
            )
          )
        }

        list.add(SoundListItem(R.layout.sound_list_item, it.key))
      }
    }
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
  fun onPlayerManagerUpdate(event: MediaPlayerService.OnPlayerManagerUpdateEvent) {
    this.players = event.players
    var showSavePresetFAB: Boolean
    Preset.readAllFromUserPreferences(requireContext()).also {
      showSavePresetFAB = !it.contains(Preset.from("", players.values))
    }

    view?.post {
      adapter?.notifyDataSetChanged()
      if (showSavePresetFAB && event.state == PlayerManager.State.PLAYING) {
        binding.savePresetButton.show()
      } else {
        binding.savePresetButton.hide()
      }

      if (event.state == PlayerManager.State.STOPPED) {
        binding.randomPresetButton.show()
      } else {
        binding.randomPresetButton.hide()
      }
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    binding = SoundLibraryFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    adapter = SoundListAdapter(requireContext())
    binding.soundList.also {
      it.setHasFixedSize(true)
      it.adapter = adapter
    }

    binding.savePresetButton.setOnLongClickListener {
      Toast.makeText(requireContext(), R.string.save_preset, Toast.LENGTH_LONG).show()
      true
    }

    binding.savePresetButton.setOnClickListener {
      DialogFragment.show(childFragmentManager) {
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
          binding.savePresetButton.hide()
          showPresetSavedMessage()

          // maybe show in-app review dialog to the user
          InAppReviewFlowManager.maybeAskForReview(requireActivity())
        }
      }
    }

    binding.randomPresetButton.setOnLongClickListener {
      Toast.makeText(requireContext(), R.string.random_preset, Toast.LENGTH_LONG).show()
      true
    }

    binding.randomPresetButton.setOnClickListener {
      DialogFragment.show(childFragmentManager) {
        val viewBinding = DialogFragmentRandomPresetBinding.inflate(layoutInflater)
        addContentView(viewBinding.root)
        title(R.string.random_preset)
        negativeButton(R.string.cancel)
        positiveButton(R.string.play) {
          eventBus.post(
            MediaPlayerService.PlayPresetEvent(
              generateRandomPreset(
                viewBinding.presetType.checkedRadioButtonId,
                viewBinding.presetIntensity.checkedRadioButtonId
              )
            )
          )

          // maybe show in-app review dialog to the user
          InAppReviewFlowManager.maybeAskForReview(requireActivity())
        }
      }
    }

    eventBus.register(this)
  }

  private fun generateRandomPreset(@IdRes type: Int, @IdRes intensity: Int): Preset {
    val tag = when (type) {
      R.id.preset_type__focus -> Sound.Tag.FOCUS
      R.id.preset_type__relax -> Sound.Tag.RELAX
      else -> null
    }

    val library = Sound.filterLibraryByTag(tag).shuffled()
    val presetSizeRange = when (intensity) {
      R.id.preset_intensity__light -> RANGE_INTENSITY_LIGHT
      R.id.preset_intensity__dense -> RANGE_INTENSITY_DENSE
      else -> RANGE_INTENSITY_ANY
    }

    val playerStates = mutableListOf<Preset.PlayerState>()
    for (i in 0 until Random.nextInt(presetSizeRange)) {
      val volume = 1 + Random.nextInt(0, Player.MAX_VOLUME)
      val timePeriod = Random.nextInt(Player.MIN_TIME_PERIOD, Player.MAX_TIME_PERIOD + 1)
      playerStates.add(
        Preset.PlayerState(library[i], volume, timePeriod)
      )
    }

    return Preset("", playerStates.toTypedArray())
  }

  override fun onDestroyView() {
    eventBus.unregister(this)
    super.onDestroyView()
  }

  private fun showPresetSavedMessage() {
    Snackbar.make(requireView(), R.string.preset_saved, Snackbar.LENGTH_LONG)
      .setAction(R.string.dismiss) { }
      .show()
  }

  private class SoundListItem(@LayoutRes val layoutID: Int, val data: String)

  private inner class SoundListAdapter(private val context: Context) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val layoutInflater = LayoutInflater.from(context)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
      return when (viewType) {
        R.layout.sound_group_list_item -> SoundGroupListItemViewHolder(
          SoundGroupListItemBinding.inflate(
            layoutInflater,
            parent,
            false
          )
        )
        R.layout.sound_list_item -> SoundListItemViewHolder(
          SoundListItemBinding.inflate(
            layoutInflater,
            parent,
            false
          )
        )
        else -> throw IllegalArgumentException("unknown view type: $viewType")
      }
    }

    override fun getItemCount(): Int {
      return dataSet.size
    }

    override fun getItemViewType(position: Int): Int {
      return dataSet[position].layoutID
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
      if (dataSet[position].layoutID == R.layout.sound_group_list_item) {
        holder as SoundGroupListItemViewHolder
        holder.binding.root.text = dataSet[position].data
        return
      }

      val soundKey = dataSet[position].data
      val sound = Sound.get(soundKey)
      val isPlaying = players.containsKey(soundKey)
      var volume = Player.DEFAULT_VOLUME
      var timePeriod = Player.DEFAULT_TIME_PERIOD
      if (isPlaying) {
        requireNotNull(players[soundKey]).also {
          volume = it.volume
          timePeriod = it.timePeriod
        }
      }

      holder as SoundListItemViewHolder
      holder.binding.playButton.isChecked = isPlaying
      holder.binding.title.text = context.getString(sound.titleResId)
      holder.binding.volumeSlider.isEnabled = isPlaying
      holder.binding.volumeSlider.value = volume.toFloat()
      holder.binding.timePeriodSlider.isEnabled = isPlaying
      holder.binding.timePeriodSlider.value = timePeriod.toFloat()
      holder.binding.timePeriodLayout.visibility = if (sound.isLooping) {
        View.GONE
      } else {
        View.VISIBLE
      }
    }
  }

  inner class SoundGroupListItemViewHolder(val binding: SoundGroupListItemBinding) :
    RecyclerView.ViewHolder(binding.root)

  inner class SoundListItemViewHolder(val binding: SoundListItemBinding) :
    RecyclerView.ViewHolder(binding.root) {

    // set listeners in holders to avoid object recreation on view recycle
    private val sliderChangeListener = object : Slider.OnChangeListener {
      override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        if (!fromUser) {
          return
        }

        val player = players[dataSet[adapterPosition].data] ?: return
        when (slider.id) {
          R.id.volume_slider -> {
            player.setVolume(value.toInt())
          }
          R.id.time_period_slider -> {
            player.timePeriod = value.toInt()
          }
        }
      }
    }

    // forces update to save preset button (by invoking onPlayerManagerUpdate)
    private val sliderTouchListener = object : Slider.OnSliderTouchListener {
      override fun onStartTrackingTouch(slider: Slider) = Unit

      override fun onStopTrackingTouch(slider: Slider) {
        eventBus.getStickyEvent(MediaPlayerService.OnPlayerManagerUpdateEvent::class.java).also {
          it ?: return
          onPlayerManagerUpdate(it)
        }
      }
    }

    init {
      setupSlider(binding.volumeSlider, 0, Player.MAX_VOLUME) {
        "${(it * 100).toInt() / Player.MAX_VOLUME}%"
      }

      setupSlider(binding.timePeriodSlider, Player.MIN_TIME_PERIOD, Player.MAX_TIME_PERIOD) {
        "${it.toInt() / 60}m ${it.toInt() % 60}s"
      }

      binding.playButton.setOnClickListener {
        val listItem = dataSet.getOrNull(adapterPosition) ?: return@setOnClickListener
        if (players.containsKey(listItem.data)) {
          eventBus.post(MediaPlayerService.StopPlayerEvent(listItem.data))
        } else {
          eventBus.post(MediaPlayerService.StartPlayerEvent(listItem.data))
        }
      }
    }

    private fun setupSlider(slider: Slider, from: Int, to: Int, formatter: (Float) -> String) {
      slider.valueFrom = from.toFloat()
      slider.valueTo = to.toFloat()
      slider.setLabelFormatter(formatter)
      slider.addOnChangeListener(sliderChangeListener)
      slider.addOnSliderTouchListener(sliderTouchListener)
    }
  }
}

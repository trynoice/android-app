package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.caverock.androidsvg.PreserveAspectRatio
import com.caverock.androidsvg.SVG
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.LibraryFragmentBinding
import com.github.ashutoshgngwr.noice.databinding.LibrarySoundGroupListItemBinding
import com.github.ashutoshgngwr.noice.databinding.LibrarySoundListItemBinding
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.NetworkInfoProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.trynoice.api.client.models.SoundGroup
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import javax.inject.Inject

@AndroidEntryPoint
class LibraryFragment : Fragment() {

  private lateinit var binding: LibraryFragmentBinding

//  private var players = emptyMap<String, Player>()

  @set:Inject
  internal lateinit var eventBus: EventBus

  @set:Inject
  internal lateinit var presetRepository: PresetRepository

  @set:Inject
  internal lateinit var settingsRepository: SettingsRepository

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  @set:Inject
  internal lateinit var reviewFlowProvider: ReviewFlowProvider

  @set:Inject
  internal lateinit var playbackController: PlaybackController

  private val viewModel: LibraryViewModel by viewModels()
  private val adapter by lazy { LibraryListAdapter(layoutInflater, settingsRepository) }

  @Subscribe(sticky = true, threadMode = ThreadMode.ASYNC)
  fun onPlayerManagerUpdate(event: MediaPlayerService.PlaybackUpdateEvent) {
//    this.players = event.players
//    val showSavePresetFAB = !presetRepository.list().contains(Preset.from("", players.values))
//
//    view?.post {
//      adapter.notifyDataSetChanged()
//      if (showSavePresetFAB && event.state == PlaybackStateCompat.STATE_PLAYING) {
//        binding.savePresetButton.show()
//      } else {
//        binding.savePresetButton.hide()
//      }
//    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = LibraryFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    val itemDecor = DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
    binding.soundList.addItemDecoration(itemDecor)
    binding.soundList.adapter = adapter

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.libraryDataSet.collect(adapter::dataSet::set)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.errorStrRes
        .filterNotNull()
        .collect { showErrorSnackbar(it) }
    }

    binding.randomPresetButton.setOnLongClickListener {
      Toast.makeText(requireContext(), R.string.random_preset, Toast.LENGTH_LONG).show()
      true
    }

    binding.randomPresetButton.setOnClickListener {
      findNavController().navigate(R.id.random_preset)
    }

    binding.savePresetButton.setOnLongClickListener {
      Toast.makeText(requireContext(), R.string.save_preset, Toast.LENGTH_LONG).show()
      true
    }

    binding.savePresetButton.setOnClickListener {
      val params = bundleOf("success" to false)
      DialogFragment.show(childFragmentManager) {
        val presets = presetRepository.list()
        title(R.string.save_preset)
        input(hintRes = R.string.name, validator = {
          when {
            it.isBlank() -> R.string.preset_name_cannot_be_empty
            presets.any { p -> it == p.name } -> R.string.preset_already_exists
            else -> 0
          }
        })

        negativeButton(R.string.cancel)
        positiveButton(R.string.save) {
//          val name = getInputText()
//          val preset = Preset.from(name, players.values)
//          presetRepository.create(preset)
//          binding.savePresetButton.hide()
//          showSuccessSnackbar(R.string.preset_saved)
//          playbackController.requestUpdateEvent()
//
//          params.putBoolean("success", true)
//          analyticsProvider.logEvent("preset_name", bundleOf("item_length" to name.length))
//          val soundCount = preset.playerStates.size
//          analyticsProvider.logEvent("preset_sounds", bundleOf("items_count" to soundCount))
//          // maybe show in-app review dialog to the user
//          reviewFlowProvider.maybeAskForReview(requireActivity())
        }

        onDismiss { analyticsProvider.logEvent("preset_create", params) }
      }
    }

    eventBus.register(this)
    analyticsProvider.setCurrentScreen("library", LibraryFragment::class)
  }

  override fun onDestroyView() {
    eventBus.unregister(this)
    super.onDestroyView()
  }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
  private val soundRepository: SoundRepository,
  private val networkInfoProvider: NetworkInfoProvider,
) : ViewModel() {

  private val soundsResource = MutableStateFlow<Resource<List<Sound>>>(Resource.Loading())

  val isLoading: StateFlow<Boolean> = soundsResource.transform { r ->
    emit(r is Resource.Loading)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  internal val libraryDataSet: StateFlow<List<LibraryListItem>> = soundsResource.transform { r ->
    var lastGroupId: String? = null
    val dataSet = mutableListOf<LibraryListItem>()
    r.data
      ?.sortedWith(compareBy({ it.group.id }, Sound::name))
      ?.forEach { sound ->
        if (lastGroupId != sound.group.id) {
          lastGroupId = sound.group.id
          dataSet.add(LibraryListItem(R.layout.library_sound_group_list_item, sound.group))
        }

        dataSet.add(LibraryListItem(R.layout.library_sound_list_item, sound))
      }

    emit(dataSet)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

  internal val errorStrRes: Flow<Int?> = soundsResource.transform { r ->
    emit(
      when (r.error) {
        null -> null
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }

  init {
    viewModelScope.launch {
      networkInfoProvider.isOnline.collect {
        loadLibrary()
      }
    }
  }

  fun loadLibrary() {
    viewModelScope.launch {
      soundRepository.list()
        .flowOn(Dispatchers.IO)
        .collect(soundsResource)
    }
  }
}

data class LibraryListItem(@LayoutRes val layoutID: Int, val data: Any)

abstract class LibraryListItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
  abstract fun bind(data: Any)
}

class SoundGroupViewHolder(
  private val binding: LibrarySoundGroupListItemBinding,
) : LibraryListItemViewHolder(binding.root) {

  override fun bind(data: Any) {
    val group = data as SoundGroup
    binding.root.text = group.name
  }
}

class SoundViewHolder(
  private val binding: LibrarySoundListItemBinding,
  private val settingsRepository: SettingsRepository,
) : LibraryListItemViewHolder(binding.root) {

  private lateinit var soundId: String

//  init {
//    binding.root.setOnClickListener {
//      dataSet.getOrNull(bindingAdapterPosition)?.also {
//        if (players.containsKey(it.data)) {
//          playbackController.stop(it.data)
//        } else {
//          playbackController.play(it.data)
//        }
//      }
//    }
//
//    binding.volumeButton.setOnClickListener { showSoundControlDialog() }
//    binding.timePeriodButton.setOnClickListener { showSoundControlDialog() }
//  }
//
//  private fun showSoundControlDialog() {
//    val listItem = dataSet.getOrNull(bindingAdapterPosition) ?: return
//    val player = players[listItem.data] ?: return
//    val sound = Sound.get(listItem.data)
//
//    DialogFragment.show(childFragmentManager) {
//      title(sound.titleResID)
//      message(R.string.volume)
//      slider(
//        viewID = R.id.volume_slider,
//        to = Player.MAX_VOLUME.toFloat(),
//        value = player.volume.toFloat(),
//        labelFormatter = { "${(it * 100).toInt() / Player.MAX_VOLUME}%" },
//        changeListener = { player.setVolume(it.toInt()) }
//      )
//
//      if (!sound.isLooping) {
//        message(R.string.repeat_time_period)
//        slider(
//          viewID = R.id.time_period_slider,
//          from = Player.MIN_TIME_PERIOD.toFloat(),
//          to = Player.MAX_TIME_PERIOD.toFloat(),
//          value = player.timePeriod.toFloat(),
//          labelFormatter = { "${it.toInt() / 60}m ${it.toInt() % 60}s" },
//          changeListener = { player.timePeriod = it.toInt() }
//        )
//      }
//
//      positiveButton(R.string.okay)
//      onDismiss { playbackController.requestUpdateEvent() }
//    }
//
//    analyticsProvider.logEvent("sound_controls_open", bundleOf("sound_key" to listItem.data))
//  }

  override fun bind(data: Any) {
    val sound = data as Sound
    soundId = sound.id
    binding.title.text = sound.name
    binding.icon.isVisible = settingsRepository.shouldDisplaySoundIcons()
    if (settingsRepository.shouldDisplaySoundIcons()) {
      binding.icon.post {
        val icon = SVG.getFromString(sound.iconSvg)
        icon.documentPreserveAspectRatio = PreserveAspectRatio.END
        icon.documentWidth = binding.icon.width.toFloat()
        icon.documentHeight = binding.icon.height.toFloat()
        val color = ContextCompat.getColor(binding.icon.context, R.color.background_darker)
        binding.icon.setSVG(icon, "svg { fill: #${Integer.toHexString(color and 0x00ffffff)} }")
      }
    }

//    binding.play.setIconResource(
//      if (sound.isPlaying) {
//        R.drawable.ic_pause_24dp
//      } else {
//        R.drawable.ic_play_arrow_24dp
//      }
//    )
//
//    @SuppressLint("SetTextI18n")
//    binding.volume.text = "${(sound.volume * 100) / Player.MAX_VOLUME}%"
//    binding.volume.isEnabled = sound.isPlaying
//
//    @SuppressLint("SetTextI18n")
//    binding.timePeriod.text = "${sound.timePeriod / 60}m ${sound.timePeriod % 60}s"
//    binding.timePeriod.isEnabled = sound.isPlaying
    binding.timePeriod.isVisible = !sound.isLooping
  }
}

class LibraryListAdapter(
  private val layoutInflater: LayoutInflater,
  private val settingsRepository: SettingsRepository,
) : RecyclerView.Adapter<LibraryListItemViewHolder>() {

  var dataSet: List<LibraryListItem> = emptyList()
    set(value) {
      val diff = DiffUtil.calculateDiff(LibraryListDiffCallback(field, value))
      field = value
      diff.dispatchUpdatesTo(this)
    }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryListItemViewHolder {
    return when (viewType) {
      R.layout.library_sound_group_list_item -> {
        val binding = LibrarySoundGroupListItemBinding.inflate(layoutInflater, parent, false)
        SoundGroupViewHolder(binding)
      }

      R.layout.library_sound_list_item -> {
        val binding = LibrarySoundListItemBinding.inflate(layoutInflater, parent, false)
        SoundViewHolder(binding, settingsRepository)
      }

      else -> throw IllegalArgumentException("unknown view type: $viewType")
    }
  }

  override fun getItemCount(): Int {
    return dataSet.size
  }

  override fun getItemViewType(position: Int): Int {
    return dataSet[position].layoutID
  }

  override fun onBindViewHolder(holder: LibraryListItemViewHolder, position: Int) {
    holder.bind(dataSet[position].data)
  }
}

private class LibraryListDiffCallback(
  private val old: List<LibraryListItem>,
  private val new: List<LibraryListItem>,
) : DiffUtil.Callback() {

  override fun getOldListSize(): Int = old.size
  override fun getNewListSize(): Int = new.size

  override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
    val oldData = old[oldPos].data
    val newData = new[newPos].data
    return when {
      oldData is Sound && newData is Sound -> oldData.id == newData.id
      oldData is SoundGroup && newData is SoundGroup -> oldData.id == newData.id
      else -> false
    }
  }

  override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
    val oldData = old[oldPos].data
    val newData = old[newPos].data
    return oldData == newData
  }
}

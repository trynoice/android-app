package com.github.ashutoshgngwr.noice.fragment

import android.annotation.SuppressLint
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
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.caverock.androidsvg.PreserveAspectRatio
import com.caverock.androidsvg.SVG
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.LibraryFragmentBinding
import com.github.ashutoshgngwr.noice.databinding.LibrarySoundGroupListItemBinding
import com.github.ashutoshgngwr.noice.databinding.LibrarySoundListItemBinding
import com.github.ashutoshgngwr.noice.engine.PlaybackController
import com.github.ashutoshgngwr.noice.engine.PlaybackState
import com.github.ashutoshgngwr.noice.ext.getInternetConnectivityFlow
import com.github.ashutoshgngwr.noice.ext.normalizeSpace
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.ext.showSuccessSnackbar
import com.github.ashutoshgngwr.noice.model.PlayerState
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LibraryFragment : Fragment(), LibraryListItemController {

  private lateinit var binding: LibraryFragmentBinding

  @set:Inject
  internal lateinit var presetRepository: PresetRepository

  @set:Inject
  internal lateinit var soundRepository: SoundRepository

  @set:Inject
  internal lateinit var settingsRepository: SettingsRepository

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  @set:Inject
  internal lateinit var reviewFlowProvider: ReviewFlowProvider

  @set:Inject
  internal lateinit var playbackController: PlaybackController

  private var isConnectedToInternet = false
  private val viewModel: LibraryViewModel by viewModels()
  private val adapter by lazy { LibraryListAdapter(layoutInflater, this) }
  private val navController by lazy {
    Navigation.findNavController(requireActivity(), R.id.home_nav_host_fragment)
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
      settingsRepository.shouldDisplaySoundIconsAsFlow().collect(adapter::setIconsEnabled)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.libraryItems.collect(adapter::setLibraryItems)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.playerStates.collect(adapter::setPlayerStates)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.isSavePresetButtonVisible.collect { isVisible ->
        if (isVisible) {
          binding.savePresetButton.show()
        } else {
          binding.savePresetButton.hide()
        }
      }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      requireContext().getInternetConnectivityFlow().collect { isConnected ->
        isConnectedToInternet = isConnected
      }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.transientErrorStrRes
        .filterNotNull()
        .filter { isConnectedToInternet } // suppress transient errors when offline.
        .collect { causeStrRes ->
          val msg = getString(R.string.library_load_error, getString(causeStrRes))
          showErrorSnackbar(msg.normalizeSpace())
        }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.persistentErrorStrRes
        .filterNotNull()
        .collect { causeStrRes ->
          val msg = getString(R.string.library_load_error, getString(causeStrRes))
          binding.errorContainer.message = msg.normalizeSpace()
        }
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
          val name = getInputText()
          val playerStates = viewModel.playerStates.value
          presetRepository.create(Preset(name, playerStates))
          binding.savePresetButton.hide()
          showSuccessSnackbar(R.string.preset_saved)
          params.putBoolean("success", true)
          analyticsProvider.logEvent("preset_name", bundleOf("item_length" to name.length))
          val soundCount = playerStates.size
          analyticsProvider.logEvent("preset_sounds", bundleOf("items_count" to soundCount))
          // maybe show in-app review dialog to the user
          reviewFlowProvider.maybeAskForReview(requireActivity())
        }

        onDismiss { analyticsProvider.logEvent("preset_create", params) }
      }
    }

    analyticsProvider.setCurrentScreen("library", LibraryFragment::class)
  }

  override fun onSoundInfoClicked(sound: Sound) {
    val args = LibrarySoundInfoFragmentArgs(sound)
    navController.navigate(R.id.library_sound_info, args.toBundle())
  }

  override fun onSoundPlayClicked(sound: Sound) {
    playbackController.play(sound.id)
  }

  override fun onSoundStopClicked(sound: Sound) {
    playbackController.stop(sound.id)
  }

  override fun onSoundVolumeClicked(sound: Sound, currentVolume: Int) {
    DialogFragment.show(childFragmentManager) {
// TODO:     title(sound.name)
      message(R.string.volume)
      slider(
        viewID = R.id.volume_slider,
        to = PlaybackController.MAX_SOUND_VOLUME.toFloat(),
        value = currentVolume.toFloat(),
        labelFormatter = { "${(it * 100).toInt() / PlaybackController.MAX_SOUND_VOLUME}%" },
        changeListener = { playbackController.setVolume(sound.id, it.toInt()) }
      )

      positiveButton(R.string.okay)
    }
  }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
  private val soundRepository: SoundRepository,
  private val presetRepository: PresetRepository,
) : ViewModel() {

  private val soundsResource = MutableSharedFlow<Resource<List<Sound>>>()
  private val playerManagerState = soundRepository.getPlayerManagerState()
  internal val playerStates = soundRepository.getPlayerStates()

  val isLoading: StateFlow<Boolean> = soundsResource.transform { r ->
    emit(r is Resource.Loading)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  internal val libraryItems: StateFlow<List<LibraryListItem>> = soundsResource.transform { r ->
    var lastGroupId: String? = null
    val dataSet = mutableListOf<LibraryListItem>()
    r.data
      ?.sortedWith(compareBy({ it.group.id }, Sound::name))
      ?.forEach { sound ->
        if (lastGroupId != sound.group.id) {
          lastGroupId = sound.group.id
          dataSet.add(LibraryListItem(R.layout.library_sound_group_list_item, group = sound.group))
        }

        dataSet.add(LibraryListItem(R.layout.library_sound_list_item, sound = sound))
      }

    emit(dataSet)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

  internal val transientErrorStrRes: Flow<Int?> = soundsResource.transform { r ->
    emit(
      when {
        r.error == null || r.data == null -> null // show persistent error if there's no data
        r.error is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }

  val persistentErrorStrRes: StateFlow<Int?> = soundsResource.transform { r ->
    emit(
      when {
        r.error == null || r.data != null -> null // show transient error if data is available
        r.error is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  internal val isSavePresetButtonVisible: StateFlow<Boolean> =
    combine(playerManagerState, playerStates) { playerManagerState, playerStates ->
      playerManagerState != PlaybackState.STOPPED
        && presetRepository.list().none { it.hasMatchingPlayerStates(playerStates) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  init {
    loadLibrary()
  }

  fun loadLibrary() {
    viewModelScope.launch {
      soundRepository.list()
        .flowOn(Dispatchers.IO)
        .collect(soundsResource)
    }
  }
}

class LibraryListAdapter(
  private val layoutInflater: LayoutInflater,
  private val itemController: LibraryListItemController,
) : RecyclerView.Adapter<LibraryListItemViewHolder>() {

  private val libraryItems = mutableListOf<LibraryListItem>()
  private val playerStates = mutableMapOf<String, PlayerState>()
  private var isIconsEnabled = false

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryListItemViewHolder {
    return when (viewType) {
      R.layout.library_sound_group_list_item -> {
        val binding = LibrarySoundGroupListItemBinding.inflate(layoutInflater, parent, false)
        SoundGroupViewHolder(binding)
      }

      R.layout.library_sound_list_item -> {
        val binding = LibrarySoundListItemBinding.inflate(layoutInflater, parent, false)
        SoundViewHolder(binding, itemController)
      }

      else -> throw IllegalArgumentException("unknown view type: $viewType")
    }
  }

  override fun getItemCount(): Int {
    return libraryItems.size
  }

  override fun getItemViewType(position: Int): Int {
    return libraryItems[position].layoutId
  }

  override fun onBindViewHolder(holder: LibraryListItemViewHolder, position: Int) {
    holder.bind(libraryItems[position], isIconsEnabled)
  }

  fun setIconsEnabled(enabled: Boolean) {
    if (isIconsEnabled == enabled) {
      return
    }

    isIconsEnabled = enabled
    notifyItemRangeChanged(0, libraryItems.size)
  }

  fun setLibraryItems(items: List<LibraryListItem>) {
    if (libraryItems.isNotEmpty()) {
      val removedCount = libraryItems.size
      libraryItems.clear()
      notifyItemRangeRemoved(0, removedCount)
    }

    libraryItems.addAll(items)
    notifyItemRangeInserted(0, items.size)
    applyPlayerStates()
  }

  fun setPlayerStates(states: Array<PlayerState>) {
    playerStates.clear()
    states.forEach { playerStates[it.soundId] = it }
    applyPlayerStates()
  }

  private fun applyPlayerStates() {
    libraryItems.forEachIndexed { i, item ->
      val newState = playerStates[item.sound?.id]
      // explicitly compare PlaybackState since PlayerState doesn't consider it in equality checks.
      if (item.playerState != newState || item.playerState?.playbackState != newState?.playbackState) {
        libraryItems[i] = item.copy(playerState = playerStates[item.sound?.id])
        notifyItemChanged(i)
      }
    }
  }
}

abstract class LibraryListItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {

  fun bind(item: LibraryListItem, isIconsEnabled: Boolean) {
    if (item.group != null) {
      bind(item.group)
    }

    if (item.sound != null) {
      bind(item.sound, item.playerState, isIconsEnabled)
    }
  }

  abstract fun bind(soundGroup: SoundGroup)
  abstract fun bind(sound: Sound, playerState: PlayerState?, isIconsEnabled: Boolean)
}

class SoundGroupViewHolder(
  private val binding: LibrarySoundGroupListItemBinding,
) : LibraryListItemViewHolder(binding.root) {

  override fun bind(soundGroup: SoundGroup) {
    binding.root.text = soundGroup.name
  }

  override fun bind(sound: Sound, playerState: PlayerState?, isIconsEnabled: Boolean) {
    throw UnsupportedOperationException()
  }
}

class SoundViewHolder(
  private val binding: LibrarySoundListItemBinding,
  private val controller: LibraryListItemController,
) : LibraryListItemViewHolder(binding.root) {

  private lateinit var sound: Sound
  private var playerState: PlayerState? = null

  init {
    binding.info.setOnClickListener { controller.onSoundInfoClicked(sound) }
    binding.play.setOnClickListener {
      if (playerState.isStopped) {
        controller.onSoundPlayClicked(sound)
      } else {
        controller.onSoundStopClicked(sound)
      }
    }

    binding.volume.setOnClickListener {
      val currentVolume = playerState?.volume ?: PlaybackController.DEFAULT_SOUND_VOLUME
      controller.onSoundVolumeClicked(sound, currentVolume)
    }
  }

  override fun bind(soundGroup: SoundGroup) {
    throw UnsupportedOperationException()
  }

  override fun bind(sound: Sound, playerState: PlayerState?, isIconsEnabled: Boolean) {
    this.sound = sound
    this.playerState = playerState

    binding.bufferingIndicator.isVisible = playerState?.playbackState == PlaybackState.BUFFERING
    binding.title.text = sound.name
    binding.icon.isVisible = isIconsEnabled
    if (isIconsEnabled) {
      binding.icon.post {
        val icon = SVG.getFromString(sound.iconSvg)
        icon.documentPreserveAspectRatio = PreserveAspectRatio.END
        icon.documentWidth = binding.icon.width.toFloat()
        icon.documentHeight = binding.icon.height.toFloat()
        val color = ContextCompat.getColor(binding.icon.context, R.color.background_darker)
        binding.icon.setSVG(icon, "svg { fill: #${Integer.toHexString(color and 0x00ffffff)} }")
      }
    }

    val isStopped = playerState.isStopped
    binding.play.setIconResource(
      if (isStopped) {
        R.drawable.ic_baseline_play_arrow_24
      } else {
        R.drawable.ic_baseline_stop_24
      }
    )

    val volume = playerState?.volume ?: PlaybackController.DEFAULT_SOUND_VOLUME
    @SuppressLint("SetTextI18n")
    binding.volume.text = "${(volume * 100) / PlaybackController.MAX_SOUND_VOLUME}%"
    binding.volume.isEnabled = !isStopped
  }

  private val PlayerState?.isStopped: Boolean
    get() = this?.playbackState?.oneOf(
      PlaybackState.IDLE,
      PlaybackState.STOPPING,
      PlaybackState.STOPPED,
      PlaybackState.FAILED
    ) ?: true
}

interface LibraryListItemController {
  fun onSoundInfoClicked(sound: Sound)
  fun onSoundPlayClicked(sound: Sound)
  fun onSoundStopClicked(sound: Sound)
  fun onSoundVolumeClicked(sound: Sound, currentVolume: Int)
}

data class LibraryListItem(
  @LayoutRes val layoutId: Int,
  val group: SoundGroup? = null,
  val sound: Sound? = null,
  val playerState: PlayerState? = null,
)

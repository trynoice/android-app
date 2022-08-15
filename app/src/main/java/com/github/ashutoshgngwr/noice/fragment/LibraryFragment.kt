package com.github.ashutoshgngwr.noice.fragment

import android.annotation.SuppressLint
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.Navigation
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
import com.github.ashutoshgngwr.noice.engine.exoplayer.SoundDownloadsRefreshWorker
import com.github.ashutoshgngwr.noice.ext.getInternetConnectivityFlow
import com.github.ashutoshgngwr.noice.ext.normalizeSpace
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.ext.showSuccessSnackBar
import com.github.ashutoshgngwr.noice.model.PlayerState
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.model.Sound
import com.github.ashutoshgngwr.noice.model.SoundDownloadState
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.trynoice.api.client.models.SoundGroup
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LibraryFragment : Fragment(), LibraryListItemController {

  private lateinit var binding: LibraryFragmentBinding

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
      viewModel.isLibraryIconsEnabled.collect(adapter::setIconsEnabled)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.isSubscribed.collect(adapter::setDownloadButtonVisible)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.libraryItems.collect(adapter::setLibraryItems)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.playerStates.collect(adapter::setPlayerStates)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.downloadStates.collect(adapter::setDownloadStates)
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
          showErrorSnackBar(msg.normalizeSpace())
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

    binding.randomPresetButton.setOnClickListener { navController.navigate(R.id.random_preset) }
    binding.randomPresetButton.setOnLongClickListener {
      Toast.makeText(requireContext(), R.string.random_preset, Toast.LENGTH_LONG).show()
      true
    }

    binding.savePresetButton.setOnClickListener {
      DialogFragment.show(childFragmentManager) {
        val presets = viewModel.listPresets()
        title(R.string.save_preset)
        val nameGetter = input(hintRes = R.string.name, validator = {
          when {
            it.isBlank() -> R.string.preset_name_cannot_be_empty
            presets.any { p -> it == p.name } -> R.string.preset_already_exists
            else -> 0
          }
        })

        negativeButton(R.string.cancel)
        positiveButton(R.string.save) {
          viewModel.saveCurrentPreset(nameGetter.invoke())
          showSuccessSnackBar(R.string.preset_saved)
          // maybe show in-app review dialog to the user
          reviewFlowProvider.maybeAskForReview(requireActivity())
        }
      }
    }

    binding.savePresetButton.setOnLongClickListener {
      Toast.makeText(requireContext(), R.string.save_preset, Toast.LENGTH_LONG).show()
      true
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
      title(sound.name)
      message(R.string.volume, textAppearance = R.style.TextAppearance_Material3_TitleLarge)
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

  override fun onSoundDownloadClicked(sound: Sound) {
    SoundDownloadsRefreshWorker.addSoundDownload(requireContext(), sound.id)
    showSuccessSnackBar(getString(R.string.sound_scheduled_for_download, sound.name))
  }

  override fun onRemoveSoundDownloadClicked(sound: Sound) {
    DialogFragment.show(childFragmentManager) {
      title(getString(R.string.remove_sound_download, sound.name))
      message(R.string.remove_sound_download_confirmation)
      negativeButton(R.string.cancel)
      positiveButton(R.string.delete) {
        SoundDownloadsRefreshWorker.removeSoundDownload(requireContext(), sound.id)
        showSuccessSnackBar(getString(R.string.sound_download_scheduled_for_removal, sound.name))
      }
    }
  }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
  private val subscriptionRepository: SubscriptionRepository,
  private val soundRepository: SoundRepository,
  private val presetRepository: PresetRepository,
  settingsRepository: SettingsRepository,
  playbackController: PlaybackController,
) : ViewModel() {

  private val soundsResource = MutableSharedFlow<Resource<List<Sound>>>()
  private val playerManagerState: StateFlow<PlaybackState> = playbackController
    .getPlayerManagerState()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), PlaybackState.STOPPED)

  internal val playerStates: StateFlow<Array<PlayerState>> = playbackController.getPlayerStates()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyArray())

  internal val downloadStates: StateFlow<Map<String, SoundDownloadState>> = soundRepository
    .getDownloadStates()
    .flowOn(Dispatchers.IO)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())

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

  internal val isSavePresetButtonVisible: StateFlow<Boolean> = combine(
    playerManagerState,
    playerStates,
    presetRepository.listFlow(),
  ) { playerManagerState, playerStates, presets ->
    playerStates
      // exclude stopping and stopped players from active preset if manager is not stopping.
      .filterNot {
        playerManagerState != PlaybackState.STOPPING
          && it.playbackState.oneOf(PlaybackState.STOPPING, PlaybackState.STOPPED)
      }
      .toTypedArray()
      .let { s ->
        playerManagerState != PlaybackState.STOPPED
          && presets.none { p -> p.hasMatchingPlayerStates(s) }
      }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  internal val isLibraryIconsEnabled: StateFlow<Boolean> = settingsRepository
    .shouldDisplaySoundIconsAsFlow()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  internal val isSubscribed = MutableStateFlow(false)

  init {
    loadLibrary()
  }

  fun loadLibrary() {
    viewModelScope.launch {
      soundRepository.list()
        .flowOn(Dispatchers.IO)
        .collect(soundsResource)
    }

    viewModelScope.launch {
      subscriptionRepository.isSubscribed()
        .flowOn(Dispatchers.IO)
        .map { it.data ?: false }
        .collect(isSubscribed)
    }
  }

  internal fun saveCurrentPreset(name: String) {
    presetRepository.create(Preset(name, playerStates.value))
  }

  internal fun listPresets(): List<Preset> {
    return presetRepository.list()
  }
}

class LibraryListAdapter(
  private val layoutInflater: LayoutInflater,
  private val itemController: LibraryListItemController,
) : RecyclerView.Adapter<LibraryListItemViewHolder>() {

  private var libraryItems = emptyList<LibraryListItem>()
  private var playerStates = emptyMap<String, PlayerState?>()
  private var isIconsEnabled = false
  private var isDownloadButtonVisible = false
  private var downloadStates = emptyMap<String, SoundDownloadState>()

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
    val soundId = libraryItems[position].sound?.id
    holder.bind(
      libraryItems[position],
      playerStates[soundId],
      isIconsEnabled,
      isDownloadButtonVisible,
      downloadStates[soundId] ?: SoundDownloadState.NOT_DOWNLOADED
    )
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
      libraryItems = emptyList()
      notifyItemRangeRemoved(0, removedCount)
    }

    libraryItems = items
    notifyItemRangeInserted(0, items.size)
  }

  fun setPlayerStates(states: Array<PlayerState>) {
    val oldStates = playerStates
    playerStates = states.associateBy { it.soundId }
    for (i in libraryItems.indices) {
      val soundId = libraryItems[i].sound?.id ?: continue
      val oldState = oldStates[soundId]
      val newState = playerStates[soundId]
      if (oldState != newState || oldState?.playbackState != newState?.playbackState) {
        notifyItemChanged(i)
      }
    }
  }

  fun setDownloadButtonVisible(isVisible: Boolean) {
    if (isDownloadButtonVisible == isVisible) {
      return
    }

    isDownloadButtonVisible = isVisible
    notifyItemRangeChanged(0, libraryItems.size)
  }

  fun setDownloadStates(states: Map<String, SoundDownloadState>) {
    val oldStates = downloadStates
    downloadStates = states
    for (i in libraryItems.indices) {
      val soundId = libraryItems[i].sound?.id ?: continue
      if (oldStates[soundId] != downloadStates[soundId]) {
        notifyItemChanged(i)
      }
    }
  }
}

abstract class LibraryListItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {

  fun bind(
    item: LibraryListItem,
    playerState: PlayerState?,
    isIconsEnabled: Boolean,
    isDownloadButtonVisible: Boolean,
    downloadState: SoundDownloadState,
  ) {
    if (item.group != null) {
      bind(item.group)
    }

    if (item.sound != null) {
      bind(item.sound, playerState, isIconsEnabled, isDownloadButtonVisible, downloadState)
    }
  }

  abstract fun bind(soundGroup: SoundGroup)

  abstract fun bind(
    sound: Sound,
    playerState: PlayerState?,
    isIconsEnabled: Boolean,
    isDownloadButtonVisible: Boolean,
    downloadState: SoundDownloadState,
  )
}

class SoundGroupViewHolder(
  private val binding: LibrarySoundGroupListItemBinding,
) : LibraryListItemViewHolder(binding.root) {

  override fun bind(soundGroup: SoundGroup) {
    binding.root.text = soundGroup.name
  }

  override fun bind(
    sound: Sound,
    playerState: PlayerState?,
    isIconsEnabled: Boolean,
    isDownloadButtonVisible: Boolean,
    downloadState: SoundDownloadState,
  ) {
    throw UnsupportedOperationException()
  }
}

class SoundViewHolder(
  private val binding: LibrarySoundListItemBinding,
  private val controller: LibraryListItemController,
) : LibraryListItemViewHolder(binding.root) {

  private lateinit var sound: Sound
  private var playerState: PlayerState? = null
  private var downloadState = SoundDownloadState.NOT_DOWNLOADED

  init {
    binding.info.setOnClickListener { controller.onSoundInfoClicked(sound) }
    binding.download.setOnClickListener {
      when (downloadState) {
        SoundDownloadState.NOT_DOWNLOADED -> controller.onSoundDownloadClicked(sound)
        else -> controller.onRemoveSoundDownloadClicked(sound)
      }
    }

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

  override fun bind(
    sound: Sound,
    playerState: PlayerState?,
    isIconsEnabled: Boolean,
    isDownloadButtonVisible: Boolean,
    downloadState: SoundDownloadState,
  ) {
    this.sound = sound
    this.playerState = playerState
    this.downloadState = downloadState

    binding.bufferingIndicator.isVisible = playerState?.playbackState == PlaybackState.BUFFERING
    binding.title.text = sound.name
    binding.icon.isVisible = isIconsEnabled
    if (isIconsEnabled) {
      val iconColor = TypedValue()
        .also { binding.icon.context.theme.resolveAttribute(R.attr.colorSurfaceVariant, it, true) }
        .data

      binding.icon.post {
        val icon = SVG.getFromString(sound.iconSvg)
        icon.documentPreserveAspectRatio = PreserveAspectRatio.END
        icon.documentWidth = binding.icon.width.toFloat()
        icon.documentHeight = binding.icon.height.toFloat()
        binding.icon.setSVG(icon, "svg { fill: #${Integer.toHexString(iconColor and 0x00ffffff)} }")
      }
    }

    binding.download.isVisible = isDownloadButtonVisible
    binding.download.setIconResource(
      when (downloadState) {
        SoundDownloadState.NOT_DOWNLOADED -> R.drawable.ic_outline_download_for_offline_24
        SoundDownloadState.DOWNLOADING -> R.drawable.ic_baseline_downloading_24
        SoundDownloadState.DOWNLOADED -> R.drawable.ic_outline_offline_pin_24
      }
    )

    (binding.download.icon as? AnimatedVectorDrawable)?.start()

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
    ) ?: true
}

interface LibraryListItemController {
  fun onSoundInfoClicked(sound: Sound)
  fun onSoundPlayClicked(sound: Sound)
  fun onSoundStopClicked(sound: Sound)
  fun onSoundVolumeClicked(sound: Sound, currentVolume: Int)
  fun onSoundDownloadClicked(sound: Sound)
  fun onRemoveSoundDownloadClicked(sound: Sound)
}

data class LibraryListItem(
  @LayoutRes val layoutId: Int,
  val group: SoundGroup? = null,
  val sound: Sound? = null,
)

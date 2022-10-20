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
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isInvisible
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
import com.github.ashutoshgngwr.noice.ext.showInfoSnackBar
import com.github.ashutoshgngwr.noice.ext.showSuccessSnackBar
import com.github.ashutoshgngwr.noice.ext.startCustomTab
import com.github.ashutoshgngwr.noice.model.PlayerState
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.models.SoundDownloadState
import com.github.ashutoshgngwr.noice.models.SoundGroup
import com.github.ashutoshgngwr.noice.models.SoundInfo
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
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
  private val mainNavController by lazy {
    Navigation.findNavController(requireActivity(), R.id.main_nav_host_fragment)
  }

  private val homeNavController by lazy {
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
      viewModel.libraryItems.collect(adapter::setLibraryItems)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.playerStates.collect(adapter::setPlayerStates)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.downloadStates.collect(adapter::setDownloadStates)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.isSubscribed
        .map { !it }
        .collect(adapter::setSoundPremiumStatusEnabled)
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

    binding.randomPresetButton.setOnClickListener { homeNavController.navigate(R.id.random_preset) }
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

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.isLibraryUpdated
        .filter { it }
        .collect {
          DialogFragment.show(childFragmentManager) {
            title(R.string.sound_library_updated)
            message(R.string.sound_library_updated_message)
            negativeButton(R.string.cancel)
            positiveButton(R.string.review) {
              requireContext().startCustomTab(R.string.sound_library_release_notes_url)
            }
          }
        }
    }

    analyticsProvider.setCurrentScreen("library", LibraryFragment::class)
  }

  override fun onSoundInfoClicked(soundInfo: SoundInfo) {
    val args = LibrarySoundInfoFragmentArgs(soundInfo)
    homeNavController.navigate(R.id.library_sound_info, args.toBundle())
  }

  override fun onSoundPlayClicked(soundInfo: SoundInfo) {
    if (soundInfo.isPremium && !viewModel.isSubscribed.value) {
      mainNavController.navigate(R.id.view_subscription_plans)
      return
    }

    playbackController.play(soundInfo.id)
  }

  override fun onSoundStopClicked(soundInfo: SoundInfo) {
    playbackController.stop(soundInfo.id)
  }

  override fun onSoundVolumeClicked(soundInfo: SoundInfo, playerState: PlayerState?) {
    if (playerState.isStopped) {
      showInfoSnackBar(R.string.play_sound_before_adjusting_volume)
      return
    }

    DialogFragment.show(childFragmentManager) {
      title(soundInfo.name)
      message(R.string.volume, textAppearance = R.style.TextAppearance_Material3_TitleLarge)
      slider(
        viewID = R.id.volume_slider,
        to = PlaybackController.MAX_SOUND_VOLUME.toFloat(),
        value = (playerState?.volume ?: PlaybackController.DEFAULT_SOUND_VOLUME).toFloat(),
        labelFormatter = { "${(it * 100).toInt() / PlaybackController.MAX_SOUND_VOLUME}%" },
        changeListener = { playbackController.setVolume(soundInfo.id, it.toInt()) }
      )

      positiveButton(R.string.okay)
    }
  }

  override fun onSoundDownloadClicked(soundInfo: SoundInfo) {
    if (!viewModel.isSubscribed.value) {
      mainNavController.navigate(R.id.view_subscription_plans)
      return
    }

    SoundDownloadsRefreshWorker.addSoundDownload(requireContext(), soundInfo.id)
    showSuccessSnackBar(getString(R.string.sound_scheduled_for_download, soundInfo.name))
  }

  override fun onRemoveSoundDownloadClicked(soundInfo: SoundInfo) {
    DialogFragment.show(childFragmentManager) {
      title(getString(R.string.remove_sound_download, soundInfo.name))
      message(R.string.remove_sound_download_confirmation)
      negativeButton(R.string.cancel)
      positiveButton(R.string.delete) {
        SoundDownloadsRefreshWorker.removeSoundDownload(requireContext(), soundInfo.id)
        getString(R.string.sound_download_scheduled_for_removal, soundInfo.name)
          .also { showSuccessSnackBar(it) }
      }
    }
  }

  override fun onPremiumStatusClicked(soundInfo: SoundInfo) {
    when {
      soundInfo.isPremium -> showInfoSnackBar(R.string.sound_is_premium)
      soundInfo.hasPremiumSegments -> showInfoSnackBar(R.string.has_premium_segments)
    }
  }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
  subscriptionRepository: SubscriptionRepository,
  private val soundRepository: SoundRepository,
  private val presetRepository: PresetRepository,
  settingsRepository: SettingsRepository,
  playbackController: PlaybackController,
) : ViewModel() {

  private val soundInfosResource = MutableSharedFlow<Resource<List<SoundInfo>>>()
  private val playerManagerState: StateFlow<PlaybackState> = playbackController
    .getPlayerManagerState()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), PlaybackState.STOPPED)

  internal val isSubscribed = subscriptionRepository.isSubscribed()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  internal val playerStates: StateFlow<Array<PlayerState>> = playbackController.getPlayerStates()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyArray())

  internal val downloadStates: StateFlow<Map<String, SoundDownloadState>> = soundRepository
    .getDownloadStates()
    .flowOn(Dispatchers.IO)
    // emit download states only when user owns an active subscription
    .combine(isSubscribed) { states, subscribed -> if (subscribed) states else emptyMap() }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyMap())

  val isLoading: StateFlow<Boolean> = soundInfosResource.transform { r ->
    emit(r is Resource.Loading)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  internal val libraryItems: StateFlow<List<LibraryListItem>> = soundInfosResource.transform { r ->
    var lastGroupId: String? = null
    val dataSet = mutableListOf<LibraryListItem>()
    r.data
      ?.sortedWith(compareBy({ it.group.id }, SoundInfo::name))
      ?.forEach { soundInfo ->
        if (lastGroupId != soundInfo.group.id) {
          lastGroupId = soundInfo.group.id
          LibraryListItem(R.layout.library_sound_group_list_item, group = soundInfo.group)
            .also { dataSet.add(it) }
        }

        dataSet.add(LibraryListItem(R.layout.library_sound_list_item, soundInfo = soundInfo))
      }

    emit(dataSet)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

  internal val transientErrorStrRes: Flow<Int?> = soundInfosResource.transform { r ->
    emit(
      when {
        r.error == null || r.data == null -> null // show persistent error if there's no data
        r.error is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }

  val persistentErrorStrRes: StateFlow<Int?> = soundInfosResource.transform { r ->
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
    !playerManagerState.oneOf(PlaybackState.STOPPING, PlaybackState.STOPPED)
      && presets.none { p -> p.hasMatchingPlayerStates(playerStates) }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  internal val isLibraryIconsEnabled: StateFlow<Boolean> = settingsRepository
    .shouldDisplaySoundIconsAsFlow()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  internal val isLibraryUpdated: StateFlow<Boolean> = soundRepository.isLibraryUpdated()
    .mapNotNull { it.data }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  init {
    loadLibrary()
  }

  fun loadLibrary() {
    viewModelScope.launch {
      soundRepository.listInfo()
        .flowOn(Dispatchers.IO)
        .collect(soundInfosResource)
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
  private var downloadStates = emptyMap<String, SoundDownloadState>()
  private var isSoundPremiumStatusEnabled = false

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
    val soundId = libraryItems[position].soundInfo?.id
    holder.bind(
      libraryItems[position],
      playerStates[soundId],
      isIconsEnabled,
      downloadStates[soundId] ?: SoundDownloadState.NOT_DOWNLOADED,
      isSoundPremiumStatusEnabled,
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
      val soundId = libraryItems[i].soundInfo?.id ?: continue
      val oldState = oldStates[soundId]
      val newState = playerStates[soundId]
      if (oldState != newState || oldState?.playbackState != newState?.playbackState) {
        notifyItemChanged(i)
      }
    }
  }

  fun setDownloadStates(states: Map<String, SoundDownloadState>) {
    val oldStates = downloadStates
    downloadStates = states
    for (i in libraryItems.indices) {
      val soundId = libraryItems[i].soundInfo?.id ?: continue
      if (oldStates[soundId] != downloadStates[soundId]) {
        notifyItemChanged(i)
      }
    }
  }

  fun setSoundPremiumStatusEnabled(enabled: Boolean) {
    if (isSoundPremiumStatusEnabled == enabled) {
      return
    }

    isSoundPremiumStatusEnabled = enabled
    notifyItemRangeChanged(0, libraryItems.size)
  }
}

abstract class LibraryListItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {

  fun bind(
    item: LibraryListItem,
    playerState: PlayerState?,
    isIconsEnabled: Boolean,
    downloadState: SoundDownloadState,
    isPremiumStatusEnabled: Boolean,
  ) {
    if (item.group != null) {
      bind(item.group)
    }

    if (item.soundInfo != null) {
      bind(item.soundInfo, playerState, isIconsEnabled, downloadState, isPremiumStatusEnabled)
    }
  }

  abstract fun bind(soundGroup: SoundGroup)

  abstract fun bind(
    soundInfo: SoundInfo,
    playerState: PlayerState?,
    isIconsEnabled: Boolean,
    downloadState: SoundDownloadState,
    isPremiumStatusEnabled: Boolean,
  )
}

class SoundGroupViewHolder(
  private val binding: LibrarySoundGroupListItemBinding,
) : LibraryListItemViewHolder(binding.root) {

  override fun bind(soundGroup: SoundGroup) {
    binding.root.text = soundGroup.name
  }

  override fun bind(
    soundInfo: SoundInfo,
    playerState: PlayerState?,
    isIconsEnabled: Boolean,
    downloadState: SoundDownloadState,
    isPremiumStatusEnabled: Boolean,
  ) {
    throw UnsupportedOperationException()
  }
}

class SoundViewHolder(
  private val binding: LibrarySoundListItemBinding,
  private val controller: LibraryListItemController,
) : LibraryListItemViewHolder(binding.root) {

  private lateinit var soundInfo: SoundInfo
  private var playerState: PlayerState? = null
  private var downloadState = SoundDownloadState.NOT_DOWNLOADED

  init {
    binding.premiumStatus.setOnClickListener { controller.onPremiumStatusClicked(soundInfo) }
    binding.info.setOnClickListener { controller.onSoundInfoClicked(soundInfo) }
    binding.download.setOnClickListener {
      when (downloadState) {
        SoundDownloadState.NOT_DOWNLOADED -> controller.onSoundDownloadClicked(soundInfo)
        else -> controller.onRemoveSoundDownloadClicked(soundInfo)
      }
    }

    binding.play.setOnClickListener {
      if (playerState.isStopped) {
        controller.onSoundPlayClicked(soundInfo)
      } else {
        controller.onSoundStopClicked(soundInfo)
      }
    }

    binding.volume.setOnClickListener {
      controller.onSoundVolumeClicked(soundInfo, playerState)
    }
  }

  override fun bind(soundGroup: SoundGroup) {
    throw UnsupportedOperationException()
  }

  override fun bind(
    soundInfo: SoundInfo,
    playerState: PlayerState?,
    isIconsEnabled: Boolean,
    downloadState: SoundDownloadState,
    isPremiumStatusEnabled: Boolean,
  ) {
    this.soundInfo = soundInfo
    this.playerState = playerState
    this.downloadState = downloadState

    binding.premiumStatus.isVisible =
      isPremiumStatusEnabled && (this.soundInfo.isPremium || this.soundInfo.hasPremiumSegments)

    binding.premiumStatus.setImageResource(
      when {
        this.soundInfo.isPremium -> R.drawable.ic_baseline_star_rate_24
        this.soundInfo.hasPremiumSegments -> R.drawable.ic_baseline_star_half_24
        else -> ResourcesCompat.ID_NULL
      }
    )

    binding.bufferingIndicator.isInvisible = playerState?.playbackState != PlaybackState.BUFFERING
    binding.title.text = this.soundInfo.name
    binding.icon.isVisible = isIconsEnabled
    if (isIconsEnabled) {
      val iconColor = TypedValue()
        .also { binding.icon.context.theme.resolveAttribute(R.attr.colorSurfaceVariant, it, true) }
        .data

      binding.icon.post {
        val icon = SVG.getFromString(this.soundInfo.iconSvg)
        icon.documentPreserveAspectRatio = PreserveAspectRatio.END
        icon.documentWidth = binding.icon.width.toFloat()
        icon.documentHeight = binding.icon.height.toFloat()
        binding.icon.setSVG(icon, "svg { fill: #${Integer.toHexString(iconColor and 0x00ffffff)} }")
      }
    }

    binding.download.setIconResource(
      when (downloadState) {
        SoundDownloadState.NOT_DOWNLOADED -> R.drawable.ic_outline_download_for_offline_24
        SoundDownloadState.DOWNLOADING -> R.drawable.ic_baseline_downloading_24
        SoundDownloadState.DOWNLOADED -> R.drawable.ic_baseline_offline_pin_24
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
  }
}

interface LibraryListItemController {
  fun onSoundInfoClicked(soundInfo: SoundInfo)
  fun onSoundPlayClicked(soundInfo: SoundInfo)
  fun onSoundStopClicked(soundInfo: SoundInfo)
  fun onSoundVolumeClicked(soundInfo: SoundInfo, playerState: PlayerState?)
  fun onSoundDownloadClicked(soundInfo: SoundInfo)
  fun onRemoveSoundDownloadClicked(soundInfo: SoundInfo)
  fun onPremiumStatusClicked(soundInfo: SoundInfo)
}

data class LibraryListItem(
  @LayoutRes val layoutId: Int,
  val group: SoundGroup? = null,
  val soundInfo: SoundInfo? = null,
)

private val PlayerState?.isStopped: Boolean
  get() = this?.playbackState?.oneOf(
    PlaybackState.IDLE,
    PlaybackState.STOPPING,
    PlaybackState.STOPPED,
  ) ?: true

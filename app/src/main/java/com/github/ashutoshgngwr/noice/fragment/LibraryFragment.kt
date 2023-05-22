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
import androidx.lifecycle.viewModelScope
import androidx.navigation.Navigation
import androidx.recyclerview.widget.RecyclerView
import com.caverock.androidsvg.PreserveAspectRatio
import com.caverock.androidsvg.SVG
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.LibraryFragmentBinding
import com.github.ashutoshgngwr.noice.databinding.LibrarySoundGroupListItemBinding
import com.github.ashutoshgngwr.noice.databinding.LibrarySoundListItemBinding
import com.github.ashutoshgngwr.noice.engine.SoundPlayer
import com.github.ashutoshgngwr.noice.engine.SoundPlayerManager
import com.github.ashutoshgngwr.noice.ext.getInternetConnectivityFlow
import com.github.ashutoshgngwr.noice.ext.launchAndRepeatOnStarted
import com.github.ashutoshgngwr.noice.ext.normalizeSpace
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.ext.showInfoSnackBar
import com.github.ashutoshgngwr.noice.ext.showSuccessSnackBar
import com.github.ashutoshgngwr.noice.metrics.AnalyticsProvider
import com.github.ashutoshgngwr.noice.metrics.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.models.SoundDownloadState
import com.github.ashutoshgngwr.noice.models.SoundGroup
import com.github.ashutoshgngwr.noice.models.SoundInfo
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.service.SoundPlaybackService
import com.github.ashutoshgngwr.noice.worker.SoundDownloadsRefreshWorker
import com.google.android.material.divider.MaterialDividerItemDecoration
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class LibraryFragment : Fragment(), SoundViewHolder.ViewController {

  private lateinit var binding: LibraryFragmentBinding

  @set:Inject
  internal var analyticsProvider: AnalyticsProvider? = null

  @set:Inject
  internal lateinit var reviewFlowProvider: ReviewFlowProvider

  @set:Inject
  internal lateinit var playbackServiceController: SoundPlaybackService.Controller

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
    binding.soundList.adapter = adapter
    MaterialDividerItemDecoration(requireContext(), MaterialDividerItemDecoration.VERTICAL)
      .also { binding.soundList.addItemDecoration(it) }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.isLibraryIconsEnabled.collect(adapter::setIconsEnabled)
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.libraryItems.collect(adapter::setLibraryItems)
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.soundStates.collect(adapter::setSoundStates)
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.soundVolumes.collect(adapter::setSoundVolumes)
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.downloadStates.collect(adapter::setDownloadStates)
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.isSubscribed
        .map { !it }
        .collect(adapter::setSoundPremiumStatusEnabled)
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.isSavePresetButtonVisible.collect { isVisible ->
        if (isVisible) {
          binding.savePresetButton.show()
        } else {
          binding.savePresetButton.hide()
        }
      }
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      requireContext().getInternetConnectivityFlow().collect { isConnected ->
        isConnectedToInternet = isConnected
      }
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.apiErrorStrRes
        .filterNotNull()
        .map { getString(R.string.library_load_error, getString(it)).normalizeSpace() }
        .collect { message ->
          if (adapter.itemCount == 0) {
            binding.errorContainer.message = message
          } else if (isConnectedToInternet) {
            showErrorSnackBar(message, snackBarAnchorView())
          }
        }
    }

    binding.randomPresetButton.setOnClickListener { homeNavController.navigate(R.id.random_preset) }
    binding.randomPresetButton.setOnLongClickListener {
      Toast.makeText(requireContext(), R.string.random_preset, Toast.LENGTH_LONG).show()
      true
    }

    binding.savePresetButton.setOnClickListener {
      DialogFragment.show(childFragmentManager) {
        title(R.string.save_preset)
        val nameGetter = input(hintRes = R.string.name, validator = { name ->
          when {
            name.isBlank() -> R.string.preset_name_cannot_be_empty
            viewModel.doesPresetExistByName(name) -> R.string.preset_already_exists
            else -> 0
          }
        })

        negativeButton(R.string.cancel)
        positiveButton(R.string.save) {
          playbackServiceController.saveCurrentPreset(nameGetter.invoke())
          showSuccessSnackBar(R.string.preset_saved, snackBarAnchorView())
          // maybe show in-app review dialog to the user
          reviewFlowProvider.maybeAskForReview(requireActivity())
        }
      }
    }

    binding.savePresetButton.setOnLongClickListener {
      Toast.makeText(requireContext(), R.string.save_preset, Toast.LENGTH_LONG).show()
      true
    }

    viewModel.loadLibrary()
    analyticsProvider?.setCurrentScreen("library", LibraryFragment::class)
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

    playbackServiceController.playSound(soundInfo.id)
  }

  override fun onSoundStopClicked(soundInfo: SoundInfo) {
    playbackServiceController.stopSound(soundInfo.id)
  }

  override fun onSoundVolumeClicked(soundInfo: SoundInfo, volume: Float) {
    DialogFragment.show(childFragmentManager) {
      title(soundInfo.name)
      message(
        resId = R.string.volume,
        textAppearance = com.google.android.material.R.style.TextAppearance_Material3_TitleLarge,
      )
      slider(
        viewID = R.id.volume_slider,
        to = 1F,
        step = 0.01F,
        value = (volume * 100).roundToInt() / 100F,  // must be a multiplier of step size.
        labelFormatter = { "${(it * 100).roundToInt()}%" },
        changeListener = { playbackServiceController.setSoundVolume(soundInfo.id, it) }
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
    getString(R.string.sound_scheduled_for_download, soundInfo.name)
      .also { showSuccessSnackBar(it, snackBarAnchorView()) }
  }

  override fun onSoundRemoveDownloadClicked(soundInfo: SoundInfo) {
    DialogFragment.show(childFragmentManager) {
      title(getString(R.string.remove_sound_download, soundInfo.name))
      message(R.string.remove_sound_download_confirmation)
      negativeButton(R.string.cancel)
      positiveButton(R.string.delete) {
        SoundDownloadsRefreshWorker.removeSoundDownload(requireContext(), soundInfo.id)
        getString(R.string.sound_download_scheduled_for_removal, soundInfo.name)
          .also { showSuccessSnackBar(it, snackBarAnchorView()) }
      }
    }
  }

  override fun onSoundPremiumStatusClicked(soundInfo: SoundInfo) {
    when {
      soundInfo.isPremium -> R.string.sound_is_premium
      soundInfo.hasPremiumSegments -> R.string.has_more_clips_with_premium
      else -> null
    }
      ?.let { showInfoSnackBar(it, snackBarAnchorView()) }
      ?.setAction(R.string.plans) { mainNavController.navigate(R.id.view_subscription_plans) }
  }

  private fun snackBarAnchorView(): View? {
    return activity?.findViewById<View?>(R.id.playback_controller)
      ?.takeIf { it.isVisible }
  }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
  subscriptionRepository: SubscriptionRepository,
  private val soundRepository: SoundRepository,
  private val presetRepository: PresetRepository,
  settingsRepository: SettingsRepository,
  playbackServiceController: SoundPlaybackService.Controller,
) : ViewModel() {

  private val soundInfosResource = MutableSharedFlow<Resource<List<SoundInfo>>>()

  val isPlaying: StateFlow<Boolean> = playbackServiceController.getState()
    .map { it != SoundPlayerManager.State.STOPPING && it != SoundPlayerManager.State.STOPPED }
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  internal val isSubscribed = subscriptionRepository.isSubscribed()
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  internal val soundStates: StateFlow<Map<String, SoundPlayer.State>> = playbackServiceController
    .getSoundStates()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

  internal val soundVolumes: StateFlow<Map<String, Float>> = playbackServiceController
    .getSoundVolumes()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

  internal val downloadStates: StateFlow<Map<String, SoundDownloadState>> = soundRepository
    .getDownloadStates()
    // emit download states only when user owns an active subscription
    .combine(isSubscribed) { states, subscribed -> if (subscribed) states else emptyMap() }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

  val isLoading: StateFlow<Boolean> = soundInfosResource.transform { r ->
    emit(r is Resource.Loading)
  }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  val libraryItems: StateFlow<List<LibraryListItem>> = soundInfosResource.transform { r ->
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
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val apiErrorStrRes: StateFlow<Int?> = soundInfosResource.map { r ->
    when (r.error) {
      null -> null
      is NetworkError -> R.string.network_error
      else -> R.string.unknown_error
    }
  }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val isSavePresetButtonVisible: StateFlow<Boolean> = playbackServiceController.getCurrentPreset()
    .combine(isPlaying) { preset, isPlaying -> preset == null && isPlaying }
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  internal val isLibraryIconsEnabled: StateFlow<Boolean> = settingsRepository
    .shouldDisplaySoundIconsAsFlow()
    .stateIn(viewModelScope, SharingStarted.Eagerly, false)

  fun loadLibrary() {
    viewModelScope.launch {
      soundRepository.listInfo().collect(soundInfosResource)
    }
  }

  internal suspend fun doesPresetExistByName(name: String): Boolean {
    return presetRepository.existsByName(name)
  }
}

class LibraryListAdapter(
  private val layoutInflater: LayoutInflater,
  private val viewController: SoundViewHolder.ViewController,
) : RecyclerView.Adapter<LibraryListItemViewHolder>() {

  private var libraryItems = emptyList<LibraryListItem>()
  private var soundStates = emptyMap<String, SoundPlayer.State>()
  private var soundVolumes = emptyMap<String, Float>()
  private var downloadStates = emptyMap<String, SoundDownloadState>()
  private var isIconsEnabled = false
  private var isSoundPremiumStatusEnabled = false

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryListItemViewHolder {
    return when (viewType) {
      R.layout.library_sound_group_list_item -> {
        val binding = LibrarySoundGroupListItemBinding.inflate(layoutInflater, parent, false)
        SoundGroupViewHolder(binding)
      }

      R.layout.library_sound_list_item -> {
        val binding = LibrarySoundListItemBinding.inflate(layoutInflater, parent, false)
        SoundViewHolder(binding, viewController)
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
      soundStates[soundId] ?: SoundPlayer.State.STOPPED,
      soundVolumes[soundId] ?: 1F,
      downloadStates[soundId] ?: SoundDownloadState.NOT_DOWNLOADED,
      isIconsEnabled,
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

  fun setSoundStates(states: Map<String, SoundPlayer.State>) {
    val oldStates = soundStates
    soundStates = states
    for (i in libraryItems.indices) {
      val soundId = libraryItems[i].soundInfo?.id ?: continue
      val oldState = oldStates[soundId]
      val newState = states[soundId]
      if (oldState != newState) {
        notifyItemChanged(i)
      }
    }
  }

  fun setSoundVolumes(volumes: Map<String, Float>) {
    val oldVolumes = soundVolumes
    soundVolumes = volumes
    for (i in libraryItems.indices) {
      val soundId = libraryItems[i].soundInfo?.id ?: continue
      val oldVolume = oldVolumes[soundId]
      val newVolume = volumes[soundId]
      if (oldVolume != newVolume) {
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
    soundState: SoundPlayer.State,
    soundVolume: Float,
    downloadState: SoundDownloadState,
    isIconsEnabled: Boolean,
    isPremiumStatusEnabled: Boolean,
  ) {
    if (item.group != null) {
      bind(item.group)
    }

    if (item.soundInfo != null) {
      bind(
        item.soundInfo,
        soundState,
        soundVolume,
        downloadState,
        isIconsEnabled,
        isPremiumStatusEnabled,
      )
    }
  }

  abstract fun bind(soundGroup: SoundGroup)

  abstract fun bind(
    soundInfo: SoundInfo,
    soundState: SoundPlayer.State,
    soundVolume: Float,
    downloadState: SoundDownloadState,
    isIconsEnabled: Boolean,
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
    soundState: SoundPlayer.State,
    soundVolume: Float,
    downloadState: SoundDownloadState,
    isIconsEnabled: Boolean,
    isPremiumStatusEnabled: Boolean
  ) {
    throw UnsupportedOperationException()
  }
}

class SoundViewHolder(
  private val binding: LibrarySoundListItemBinding,
  private val controller: ViewController,
) : LibraryListItemViewHolder(binding.root) {

  private lateinit var soundInfo: SoundInfo
  private var soundState = SoundPlayer.State.STOPPED
  private var soundVolume = 1F
  private var downloadState = SoundDownloadState.NOT_DOWNLOADED

  init {
    binding.premiumStatus.setOnClickListener { controller.onSoundPremiumStatusClicked(soundInfo) }
    binding.info.setOnClickListener { controller.onSoundInfoClicked(soundInfo) }
    binding.download.setOnClickListener {
      when (downloadState) {
        SoundDownloadState.NOT_DOWNLOADED -> controller.onSoundDownloadClicked(soundInfo)
        else -> controller.onSoundRemoveDownloadClicked(soundInfo)
      }
    }

    binding.play.setOnClickListener {
      if (soundState == SoundPlayer.State.STOPPING || soundState == SoundPlayer.State.STOPPED) {
        controller.onSoundPlayClicked(soundInfo)
      } else {
        controller.onSoundStopClicked(soundInfo)
      }
    }

    binding.volume.setOnClickListener {
      controller.onSoundVolumeClicked(soundInfo, soundVolume)
    }
  }

  override fun bind(soundGroup: SoundGroup) {
    throw UnsupportedOperationException()
  }

  override fun bind(
    soundInfo: SoundInfo,
    soundState: SoundPlayer.State,
    soundVolume: Float,
    downloadState: SoundDownloadState,
    isIconsEnabled: Boolean,
    isPremiumStatusEnabled: Boolean
  ) {
    this.soundInfo = soundInfo
    this.soundState = soundState
    this.soundVolume = soundVolume
    this.downloadState = downloadState

    binding.premiumStatus.isVisible =
      isPremiumStatusEnabled && (this.soundInfo.isPremium || this.soundInfo.hasPremiumSegments)

    binding.premiumStatus.setImageResource(
      when {
        this.soundInfo.isPremium -> R.drawable.round_star_24
        this.soundInfo.hasPremiumSegments -> R.drawable.round_star_half_24
        else -> ResourcesCompat.ID_NULL
      }
    )

    binding.bufferingIndicator.isInvisible = soundState != SoundPlayer.State.BUFFERING
    binding.title.text = soundInfo.name
    binding.icon.isVisible = isIconsEnabled
    if (isIconsEnabled) {
      val iconColor = TypedValue()
        .also { v ->
          binding.icon.context.theme.resolveAttribute(
            com.google.android.material.R.attr.colorSurfaceVariant,
            v,
            true,
          )
        }
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
      if (downloadState == SoundDownloadState.DOWNLOADED) {
        R.drawable.round_download_done_24
      } else {
        R.drawable.round_download_24
      }
    )

    (binding.download.icon as? AnimatedVectorDrawable)?.also { anim ->
      if (downloadState == SoundDownloadState.DOWNLOADING) {
        anim.start()
      } else {
        anim.stop()
      }
    }

    binding.play.setIconResource(
      if (soundState == SoundPlayer.State.STOPPING || soundState == SoundPlayer.State.STOPPED) {
        R.drawable.round_play_arrow_24
      } else {
        R.drawable.round_stop_24
      }
    )

    @SuppressLint("SetTextI18n")
    binding.volume.text = "${(soundVolume * 100).roundToInt()}%"
  }


  interface ViewController {
    fun onSoundInfoClicked(soundInfo: SoundInfo)
    fun onSoundPlayClicked(soundInfo: SoundInfo)
    fun onSoundStopClicked(soundInfo: SoundInfo)
    fun onSoundVolumeClicked(soundInfo: SoundInfo, volume: Float)
    fun onSoundDownloadClicked(soundInfo: SoundInfo)
    fun onSoundRemoveDownloadClicked(soundInfo: SoundInfo)
    fun onSoundPremiumStatusClicked(soundInfo: SoundInfo)
  }
}

data class LibraryListItem(
  @LayoutRes val layoutId: Int,
  val group: SoundGroup? = null,
  val soundInfo: SoundInfo? = null,
)

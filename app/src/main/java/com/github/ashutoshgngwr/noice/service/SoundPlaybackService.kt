package com.github.ashutoshgngwr.noice.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.os.postDelayed
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioAttributesCompat
import androidx.media3.datasource.cache.Cache
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.activity.MainActivity
import com.github.ashutoshgngwr.noice.cast.CastReceiverUiManager
import com.github.ashutoshgngwr.noice.engine.AudioFocusManager
import com.github.ashutoshgngwr.noice.engine.DefaultMediaPlayer
import com.github.ashutoshgngwr.noice.engine.LocalSoundPlayer
import com.github.ashutoshgngwr.noice.engine.SoundPlaybackNotificationManager
import com.github.ashutoshgngwr.noice.engine.SoundPlayer
import com.github.ashutoshgngwr.noice.engine.SoundPlayerManager
import com.github.ashutoshgngwr.noice.engine.SoundPlayerManagerMediaSession
import com.github.ashutoshgngwr.noice.engine.exoplayer.SoundDataSourceFactory
import com.github.ashutoshgngwr.noice.ext.bindServiceCallbackFlow
import com.github.ashutoshgngwr.noice.ext.getSerializableCompat
import com.github.ashutoshgngwr.noice.models.Preset
import com.github.ashutoshgngwr.noice.provider.CastApiProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.trynoice.api.client.NoiceApiClient
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

@AndroidEntryPoint
class SoundPlaybackService : LifecycleService(), SoundPlayerManager.Listener,
  CastApiProvider.SessionListener {

  @set:Inject
  internal lateinit var presetRepository: PresetRepository

  @set:Inject
  internal lateinit var soundRepository: SoundRepository

  @set:Inject
  internal lateinit var subscriptionRepository: SubscriptionRepository

  @set:Inject
  internal lateinit var settingsRepository: SettingsRepository

  @set:Inject
  internal lateinit var apiClient: NoiceApiClient

  @set:Inject
  internal lateinit var soundDownloadCache: Cache

  @set:Inject
  internal lateinit var castApiProvider: CastApiProvider

  private var castReceiverUiManager: CastReceiverUiManager? = null
  private val handler = Handler(Looper.getMainLooper())
  private val soundPlayerStates = mutableMapOf<String, SoundPlayer.State>()
  private val soundPlayerVolumes = mutableMapOf<String, Float>()

  private val soundPlayerManagerStateFlow = MutableStateFlow(SoundPlayerManager.State.STOPPED)
  private val soundPlayerManagerVolumeFlow = MutableStateFlow(1F)
  private val soundPlayerStatesFlow = MutableStateFlow(soundPlayerStates.toMap())
  private val soundPlayerVolumesFlow = MutableStateFlow(soundPlayerVolumes.toMap())
  private val currentSoundStatesFlow = MutableStateFlow(sortedMapOf<String, Float>())
  private val currentPresetFlow = currentSoundStatesFlow
    .flatMapLatest { presetRepository.getBySoundStatesFlow(it) }
    .stateIn(lifecycleScope, SharingStarted.WhileSubscribed(), null)

  private val serviceBinder = Binder(
    soundPlayerManagerStateFlow = soundPlayerManagerStateFlow,
    soundPlayerManagerVolumeFlow = soundPlayerManagerVolumeFlow,
    soundPlayerStatesFlow = soundPlayerStatesFlow,
    soundPlayerVolumesFlow = soundPlayerVolumesFlow,
    currentPresetFlow = currentPresetFlow,
  )

  private val mainActivityPi: PendingIntent by lazy {
    var piFlags = PendingIntent.FLAG_UPDATE_CURRENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      piFlags = piFlags or PendingIntent.FLAG_IMMUTABLE
    }

    PendingIntent.getActivity(this, 0x28, Intent(this, MainActivity::class.java), piFlags)
  }

  private val mediaSession: SoundPlayerManagerMediaSession by lazy {
    SoundPlayerManagerMediaSession(this, mainActivityPi)
  }

  private val notificationManager: SoundPlaybackNotificationManager by lazy {
    SoundPlaybackNotificationManager(
      service = this,
      mediaSessionToken = mediaSession.getSessionToken(),
      contentPi = mainActivityPi,
      resumePi = buildResumeActionPendingIntent(this),
      pausePi = buildPauseActionPendingIntent(this),
      stopPi = buildStopActionPendingIntent(this),
      randomPresetPi = buildRandomPresetActionPendingIntent(this),
      skipToNextPresetPi = buildSkipToNextPresetActionPendingIntent(this),
      skipToPrevPresetPi = buildSkipToPrevPresetActionPendingIntent(this),
    )
  }

  private val soundDataSourceFactory: SoundDataSourceFactory by lazy {
    SoundDataSourceFactory(apiClient, soundDownloadCache)
  }

  private val localSoundPlayerFactory: SoundPlayer.Factory by lazy {
    LocalSoundPlayer.Factory(
      soundRepository = soundRepository,
      mediaPlayerFactory = DefaultMediaPlayer.Factory(this, soundDataSourceFactory),
      defaultScope = lifecycleScope,
    )
  }

  private val audioFocusManager: AudioFocusManager by lazy { AudioFocusManager(this) }
  private val soundPlayerManager: SoundPlayerManager by lazy {
    SoundPlayerManager(
      soundPlayerFactory = localSoundPlayerFactory,
      audioFocusManager = audioFocusManager,
      listener = this,
    )
  }

  private val wakeLock: PowerManager.WakeLock by lazy {
    requireNotNull(getSystemService<PowerManager>())
      .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "noice:PlaybackService")
      .apply { setReferenceCounted(false) }
  }

  private val mediaSessionCallback = object : SoundPlayerManagerMediaSession.Callback {
    override fun onPlay() = soundPlayerManager.resume()
    override fun onStop() = soundPlayerManager.stop(false)
    override fun onPause() = soundPlayerManager.pause(false)
    override fun onSkipToPrevious() = skipPreset(PRESET_SKIP_DIRECTION_PREV)
    override fun onSkipToNext() = skipPreset(PRESET_SKIP_DIRECTION_NEXT)
  }

  private val becomingNoisyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
        Log.d(LOG_TAG, "onReceive: becoming noisy, immediately pausing playback")
        soundPlayerManager.pause(true)
      }
    }
  }

  override fun onBind(intent: Intent): IBinder {
    super.onBind(intent)
    return serviceBinder
  }

  override fun onCreate() {
    super.onCreate()
    registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    castApiProvider.registerSessionListener(this)

    lifecycleScope.launch {
      currentPresetFlow.collect { preset ->
        notificationManager.setCurrentPresetName(preset?.name)
        mediaSession.setCurrentPresetName(preset?.name)
        castReceiverUiManager?.setPresetName(preset?.name)
      }
    }

    lifecycleScope.launch {
      subscriptionRepository.isSubscribed().collect { subscribed ->
        soundDataSourceFactory.enableDownloadedSounds = subscribed
        soundPlayerManager.setPremiumSegmentsEnabled(subscribed)
      }
    }

    // watch and adapt user settings as they change.
    lifecycleScope.launch {
      settingsRepository.shouldIgnoreAudioFocusChangesAsFlow()
        .collect { audioFocusManager.setDisabled(it) }
    }

    lifecycleScope.launch {
      settingsRepository.isMediaButtonsEnabledAsFlow().collect { isEnabled ->
        mediaSession.setCallback(if (isEnabled) mediaSessionCallback else null)
      }
    }

    lifecycleScope.launch {
      settingsRepository.getSoundFadeInDurationAsFlow()
        .collect { soundPlayerManager.setFadeInDuration(it) }
    }

    lifecycleScope.launch {
      settingsRepository.getSoundFadeOutDurationAsFlow()
        .collect { soundPlayerManager.setFadeOutDuration(it) }
    }

    lifecycleScope.launch {
      val audioQualityFlow = settingsRepository.getAudioQualityAsFlow()
      val isSubscribedFlow = subscriptionRepository.isSubscribed()
      combine(audioQualityFlow, isSubscribedFlow) { quality, subscribed ->
        if (subscribed) quality else SettingsRepository.FREE_AUDIO_QUALITY
      }.collect { soundPlayerManager.setAudioBitrate(it.bitrate) }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_PLAY_SOUND -> soundPlayerManager.playSound(getSoundIdExtra(intent))
      ACTION_STOP_SOUND -> soundPlayerManager.stopSound(getSoundIdExtra(intent))
      ACTION_RESUME -> soundPlayerManager.resume()
      ACTION_PAUSE -> soundPlayerManager.pause(
        intent.getBooleanExtra(INTENT_EXTRA_SKIP_FADE_TRANSITION, false)
      )

      ACTION_STOP -> soundPlayerManager.stop(false)

      ACTION_SET_VOLUME -> {
        val volume = intent.getFloatExtra(INTENT_EXTRA_VOLUME, -1F)
        soundPlayerManager.setVolume(volume)
      }

      ACTION_SET_SOUND_VOLUME -> {
        val volume = intent.getFloatExtra(INTENT_EXTRA_VOLUME, -1F)
        soundPlayerManager.setSoundVolume(getSoundIdExtra(intent), volume)
      }

      ACTION_PLAY_PRESET -> {
        val preset = intent.extras?.getSerializableCompat(INTENT_EXTRA_PRESET, Preset::class)
        requireNotNull(preset) { "intent extra '${INTENT_EXTRA_PRESET}' is required to send '${ACTION_PLAY_PRESET}' command" }
        soundPlayerManager.playPreset(preset.soundStates)
      }

      ACTION_CLEAR_STOP_SCHEDULE -> handler.removeCallbacksAndMessages(STOP_CALLBACK_TOKEN)
      ACTION_SCHEDULE_STOP -> {
        val atMillis = intent.getLongExtra(INTENT_EXTRA_SCHEDULED_STOP_AT_MILLIS, -1)
        val remaining = atMillis - System.currentTimeMillis()
        require(remaining > 0) { "intent extra '${INTENT_EXTRA_SCHEDULED_STOP_AT_MILLIS}' must be greater than current timestamp" }
        handler.removeCallbacksAndMessages(STOP_CALLBACK_TOKEN)
        handler.postDelayed(remaining, STOP_CALLBACK_TOKEN) { soundPlayerManager.pause(false) }
      }

      ACTION_SET_AUDIO_USAGE -> when (intent.getStringExtra(INTENT_EXTRA_AUDIO_USAGE)) {
        AUDIO_USAGE_MEDIA -> {
          soundPlayerManager.setAudioAttributes(SoundPlayerManager.DEFAULT_AUDIO_ATTRIBUTES)
          mediaSession.setAudioStream(AudioManager.STREAM_MUSIC)
        }

        AUDIO_USAGE_ALARM -> {
          soundPlayerManager.setAudioAttributes(SoundPlayerManager.ALARM_AUDIO_ATTRIBUTES)
          mediaSession.setAudioStream(AudioManager.STREAM_ALARM)
        }

        else -> throw IllegalArgumentException(
          """
            intent extra '${INTENT_EXTRA_AUDIO_USAGE}' must be be one of
            '${AudioAttributesCompat.USAGE_MEDIA}' or '${AudioAttributesCompat.USAGE_ALARM}'"
          """.trimIndent()
        )
      }

      ACTION_SKIP_PRESET -> {
        val skipDir = intent.getIntExtra(INTENT_EXTRA_PRESET_SKIP_DIRECTION, 0)
        require(skipDir == PRESET_SKIP_DIRECTION_PREV || skipDir == PRESET_SKIP_DIRECTION_NEXT) {
          """
            intent extra '${INTENT_EXTRA_PRESET_SKIP_DIRECTION}=${skipDir}' must be be one of
            '${PRESET_SKIP_DIRECTION_PREV}' or '${PRESET_SKIP_DIRECTION_NEXT}'"
          """.trimIndent()
        }

        skipPreset(skipDir)
      }

      ACTION_PLAY_RANDOM_PRESET -> {
        lifecycleScope.launch {
          presetRepository.generate(emptySet(), Random.nextInt(2, 6))
            .lastOrNull()
            ?.data
            ?.also { soundPlayerManager.playPreset(it.soundStates) }
        }
      }

      ACTION_SAVE_CURRENT_PRESET -> {
        val presetName = intent.getStringExtra(INTENT_EXTRA_PRESET_NAME)
        require(presetName != null) { "$INTENT_EXTRA_PRESET_NAME must not be null" }
        val preset = Preset(presetName, soundPlayerManager.getCurrentPreset())
        lifecycleScope.launch {
          require(!presetRepository.existsByName(preset.name)) { "preset with this name already exists" }
          presetRepository.save(preset)
        }
      }
    }

    return super.onStartCommand(intent, flags, startId)
  }

  override fun onDestroy() {
    Log.d(LOG_TAG, "onDestroy: releasing acquired resources")
    soundPlayerManager.stop(true)
    mediaSession.release()
    castApiProvider.unregisterSessionListener(this)
    unregisterReceiver(becomingNoisyReceiver)
    if (wakeLock.isHeld) {
      wakeLock.release()
    }

    super.onDestroy()
  }

  override fun onCastSessionBegin() {
    Log.d(LOG_TAG, "onCastSessionBegin: switching playback to remote")
    castReceiverUiManager = castApiProvider.getReceiverUiManager()
    // only need to set the preset name here as changing the sound player factory will refresh the
    // other ui state implicitly.
    castReceiverUiManager?.setPresetName(currentPresetFlow.value?.name)

    mediaSession.setPlaybackToRemote(castApiProvider.getVolumeProvider())
    soundPlayerManager.setSoundPlayerFactory(castApiProvider.getSoundPlayerFactory())
  }

  override fun onCastSessionEnd() {
    Log.i(LOG_TAG, "onCastSessionEnd: switching playback to local")
    castReceiverUiManager = null
    mediaSession.setPlaybackToLocal()
    soundPlayerManager.setSoundPlayerFactory(localSoundPlayerFactory)
  }

  override fun onSoundPlayerManagerStateChange(state: SoundPlayerManager.State) {
    soundPlayerManagerStateFlow.value = state
    mediaSession.setState(state)
    notificationManager.setState(state)
    castReceiverUiManager?.setSoundPlayerManagerState(state, soundPlayerManagerVolumeFlow.value)
    if (state == SoundPlayerManager.State.PAUSED) {
      handler.postDelayed(10.minutes.inWholeMilliseconds, IDLE_TIMEOUT_CALLBACK_TOKEN) {
        soundPlayerManager.stop(true)
      }
    } else {
      handler.removeCallbacksAndMessages(IDLE_TIMEOUT_CALLBACK_TOKEN)
    }

    if (state == SoundPlayerManager.State.STOPPED) {
      Log.d(LOG_TAG, "onSoundPlayerManagerStateChange: playback stopped, releasing resources")
      wakeLock.release()
    } else {
      Log.d(LOG_TAG, "onSoundPlayerManagerStateChange: playback ongoing, ensuring resources")
      wakeLock.acquire(WAKELOCK_TIMEOUT)
    }
  }

  override fun onSoundPlayerManagerVolumeChange(volume: Float) {
    soundPlayerManagerVolumeFlow.value = volume
    castReceiverUiManager?.setSoundPlayerManagerState(soundPlayerManagerStateFlow.value, volume)
  }

  override fun onSoundStateChange(soundId: String, state: SoundPlayer.State) {
    soundPlayerStates[soundId] = state
    soundPlayerStatesFlow.value = soundPlayerStates.toMap()
    castReceiverUiManager?.setSoundPlayerState(soundId, state, soundPlayerVolumes[soundId] ?: 1F)
    onCurrentPresetChange()
  }

  override fun onSoundVolumeChange(soundId: String, volume: Float) {
    soundPlayerVolumes[soundId] = volume
    soundPlayerVolumesFlow.value = soundPlayerVolumes.toMap()
    castReceiverUiManager?.setSoundPlayerState(
      soundId,
      soundPlayerStates[soundId] ?: SoundPlayer.State.STOPPED,
      volume,
    )

    onCurrentPresetChange()
  }

  @VisibleForTesting
  fun onCurrentPresetChange() {
    currentSoundStatesFlow.value = soundPlayerManager.getCurrentPreset()
  }

  private fun getSoundIdExtra(intent: Intent): String {
    return requireNotNull(intent.getStringExtra(INTENT_EXTRA_SOUND_ID)) {
      "intent extra '${INTENT_EXTRA_SOUND_ID}' is required to send '${intent.action}' command"
    }
  }

  private fun skipPreset(direction: Int) {
    val current = currentPresetFlow.value ?: return
    lifecycleScope.launch {
      val nextPreset = if (direction < 0) {
        presetRepository.getPreviousPreset(current)
      } else {
        presetRepository.getNextPreset(current)
      }

      if (nextPreset == null) {
        return@launch
      }

      soundPlayerManager.playPreset(nextPreset.soundStates)
    }
  }

  companion object {
    private const val LOG_TAG = "SoundPlaybackService"
    private const val WAKELOCK_TIMEOUT = 24 * 60 * 60 * 1000L
    private const val STOP_CALLBACK_TOKEN = "stopCallback"
    private const val IDLE_TIMEOUT_CALLBACK_TOKEN = "idleTimeoutCallback"

    private const val ACTION_PLAY_SOUND = "playSound"
    private const val ACTION_STOP_SOUND = "stopSound"
    private const val ACTION_PAUSE = "pause"
    private const val ACTION_RESUME = "resume"
    private const val ACTION_STOP = "stop"
    private const val ACTION_PLAY_PRESET = "playPreset"
    private const val ACTION_SCHEDULE_STOP = "scheduleStop"
    private const val ACTION_CLEAR_STOP_SCHEDULE = "clearStopSchedule"
    private const val ACTION_SET_AUDIO_USAGE = "setAudioUsage"
    private const val ACTION_SKIP_PRESET = "skipPreset"
    private const val ACTION_SET_VOLUME = "setVolume"
    private const val ACTION_SET_SOUND_VOLUME = "setSoundVolume"
    private const val ACTION_PLAY_RANDOM_PRESET = "playRandomPreset"
    private const val ACTION_SAVE_CURRENT_PRESET = "saveCurrentPreset"

    private const val INTENT_EXTRA_SOUND_ID = "soundId"
    private const val INTENT_EXTRA_PRESET = "preset"
    private const val INTENT_EXTRA_SCHEDULED_STOP_AT_MILLIS = "scheduledStopAtMillis"
    private const val INTENT_EXTRA_AUDIO_USAGE = "audioUsage"
    private const val INTENT_EXTRA_PRESET_SKIP_DIRECTION = "presetSkipDirection"
    private const val INTENT_EXTRA_VOLUME = "volume"
    private const val INTENT_EXTRA_SKIP_FADE_TRANSITION = "skipFadeTransition"
    private const val INTENT_EXTRA_PRESET_NAME = "presetName"

    private const val PRESET_SKIP_DIRECTION_NEXT = 1
    private const val PRESET_SKIP_DIRECTION_PREV = -1

    private const val AUDIO_USAGE_MEDIA = "media"
    private const val AUDIO_USAGE_ALARM = "alarm"

    /**
     * Returns a [PendingIntent] that sends [ACTION_RESUME] command to the [SoundPlaybackService].
     */
    private fun buildResumeActionPendingIntent(context: Context): PendingIntent {
      return buildPendingIntent(context, 0x3A, true) { action = ACTION_RESUME }
    }

    /**
     * Returns a [PendingIntent] that sends [ACTION_PAUSE] command to the [SoundPlaybackService].
     */
    private fun buildPauseActionPendingIntent(context: Context): PendingIntent {
      return buildPendingIntent(context, 0x3B, false) { action = ACTION_PAUSE }
    }

    /**
     * Returns a [PendingIntent] that sends [ACTION_STOP] command to the [SoundPlaybackService].
     */
    private fun buildStopActionPendingIntent(context: Context): PendingIntent {
      return buildPendingIntent(context, 0x3C, false) { action = ACTION_STOP }
    }

    /**
     * Returns a [PendingIntent] that sends [ACTION_PLAY_RANDOM_PRESET] command to the
     * [SoundPlaybackService].
     */
    private fun buildRandomPresetActionPendingIntent(context: Context): PendingIntent {
      return buildPendingIntent(context, 0x3D, true) { action = ACTION_PLAY_RANDOM_PRESET }
    }

    /**
     * Returns a [PendingIntent] that sends [ACTION_SKIP_PRESET] command with
     * [PRESET_SKIP_DIRECTION_PREV] to the [SoundPlaybackService].
     */
    private fun buildSkipToPrevPresetActionPendingIntent(context: Context): PendingIntent {
      return buildPendingIntent(context, 0x3E, true) {
        action = ACTION_SKIP_PRESET
        putExtra(INTENT_EXTRA_PRESET_SKIP_DIRECTION, PRESET_SKIP_DIRECTION_PREV)
      }
    }

    /**
     * Returns a [PendingIntent] that sends [ACTION_SKIP_PRESET] command with
     * [PRESET_SKIP_DIRECTION_NEXT] to the [SoundPlaybackService].
     */
    private fun buildSkipToNextPresetActionPendingIntent(context: Context): PendingIntent {
      return buildPendingIntent(context, 0x3F, true) {
        action = ACTION_SKIP_PRESET
        putExtra(INTENT_EXTRA_PRESET_SKIP_DIRECTION, PRESET_SKIP_DIRECTION_NEXT)
      }
    }

    private inline fun buildPendingIntent(
      context: Context,
      requestCode: Int,
      foreground: Boolean,
      intentBuilder: Intent.() -> Unit,
    ): PendingIntent {
      val piFlags = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      }

      val intent = Intent(context, SoundPlaybackService::class.java)
      intentBuilder.invoke(intent)
      return if (foreground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        PendingIntent.getForegroundService(context, requestCode, intent, piFlags)
      } else {
        PendingIntent.getService(context, requestCode, intent, piFlags)
      }
    }
  }

  private class Binder(
    val soundPlayerManagerStateFlow: Flow<SoundPlayerManager.State>,
    val soundPlayerManagerVolumeFlow: Flow<Float>,
    val soundPlayerStatesFlow: Flow<Map<String, SoundPlayer.State>>,
    val soundPlayerVolumesFlow: Flow<Map<String, Float>>,
    val currentPresetFlow: Flow<Preset?>,
  ) : android.os.Binder()

  @Singleton
  class Controller @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    /**
     * Sends the start command to the service with [ACTION_PLAY_SOUND].
     */
    fun playSound(soundId: String) {
      commandSoundPlaybackService(true) {
        action = ACTION_PLAY_SOUND
        putExtra(INTENT_EXTRA_SOUND_ID, soundId)
      }
    }

    /**
     * Sends the start command to the service with [ACTION_STOP_SOUND].
     */
    fun stopSound(soundId: String) {
      commandSoundPlaybackService(false) {
        action = ACTION_STOP_SOUND
        putExtra(INTENT_EXTRA_SOUND_ID, soundId)
      }
    }

    /**
     * Sends the start command to the service with [ACTION_SET_VOLUME].
     */
    fun setVolume(volume: Float) {
      require(volume in 0F..1F) { "volume must be in range [0, 1], got: $volume" }
      commandSoundPlaybackService(false) {
        action = ACTION_SET_VOLUME
        putExtra(INTENT_EXTRA_VOLUME, volume)
      }
    }

    /**
     * Sends the start command to the service with [ACTION_SET_SOUND_VOLUME].
     */
    fun setSoundVolume(soundId: String, volume: Float) {
      require(volume in 0F..1F) { "volume must be in range [0, 1], got: $volume" }
      commandSoundPlaybackService(false) {
        action = ACTION_SET_SOUND_VOLUME
        putExtra(INTENT_EXTRA_SOUND_ID, soundId)
        putExtra(INTENT_EXTRA_VOLUME, volume)
      }
    }

    /**
     * Sends the start command to the service with [ACTION_PAUSE].
     */
    fun pause(skipFadeTransition: Boolean = false) {
      commandSoundPlaybackService(false) {
        action = ACTION_PAUSE
        putExtra(INTENT_EXTRA_SKIP_FADE_TRANSITION, skipFadeTransition)
      }
    }

    /**
     * Sends the start command to the service with [ACTION_RESUME].
     */
    fun resume() {
      commandSoundPlaybackService(true) { action = ACTION_RESUME }
    }

    /**
     * Sends the start command to the service with [ACTION_STOP].
     */
    fun stop() {
      commandSoundPlaybackService(false) { action = ACTION_STOP }
    }

    /**
     * Sends the start command to the service with [ACTION_PLAY_PRESET].
     */
    fun playPreset(preset: Preset) {
      commandSoundPlaybackService(true) {
        action = ACTION_PLAY_PRESET
        putExtra(INTENT_EXTRA_PRESET, preset)
      }
    }

    /**
     * Sends the start command to the service with [ACTION_SCHEDULE_STOP].
     */
    fun scheduleStop(afterDurationMillis: Long) {
      val atMillis = System.currentTimeMillis() + afterDurationMillis
      prefs.edit(commit = true) { putLong(PREF_SCHEDULED_STOP_MILLIS, atMillis) }
      commandSoundPlaybackService(false) {
        action = ACTION_SCHEDULE_STOP
        putExtra(INTENT_EXTRA_SCHEDULED_STOP_AT_MILLIS, atMillis)
      }
    }

    /**
     * Sends the start command to the service with [ACTION_CLEAR_STOP_SCHEDULE].
     */
    fun clearStopSchedule() {
      prefs.edit { remove(PREF_SCHEDULED_STOP_MILLIS) }
      commandSoundPlaybackService(false) { action = ACTION_CLEAR_STOP_SCHEDULE }
    }

    /**
     * Returns the remaining duration millis for the last automatic stop schedule for the sound
     * playback service.
     */
    fun getStopScheduleRemainingMillis(): Long {
      return max(prefs.getLong(PREF_SCHEDULED_STOP_MILLIS, 0) - System.currentTimeMillis(), 0)
    }

    /**
     * Sends the start command to the service with [ACTION_SET_AUDIO_USAGE].
     *
     * @param usage must be one of [AUDIO_USAGE_MEDIA] or [AUDIO_USAGE_ALARM].
     */
    fun setAudioUsage(usage: String) {
      commandSoundPlaybackService(false) {
        action = ACTION_SET_AUDIO_USAGE
        putExtra(INTENT_EXTRA_AUDIO_USAGE, usage)
      }
    }

    /**
     * Sends the start command to the service with [ACTION_SAVE_CURRENT_PRESET].
     */
    fun saveCurrentPreset(presetName: String) {
      commandSoundPlaybackService(false) {
        action = ACTION_SAVE_CURRENT_PRESET
        putExtra(INTENT_EXTRA_PRESET_NAME, presetName)
      }
    }

    /**
     * @return a [Flow] that emits the current [SoundPlayerManager.State] of the
     * [SoundPlayerManager] instance being used by the [SoundPlaybackService].
     */
    fun getState(): Flow<SoundPlayerManager.State> =
      context.bindServiceCallbackFlow<SoundPlaybackService, Binder, SoundPlayerManager.State> { binder ->
        binder.soundPlayerManagerStateFlow
      }

    /**
     * @return a [Flow] that emits the current volume of the [SoundPlayerManager] instance being
     * used by the [SoundPlaybackService].
     */
    fun getVolume(): Flow<Float> =
      context.bindServiceCallbackFlow<SoundPlaybackService, Binder, Float> { binder ->
        binder.soundPlayerManagerVolumeFlow
      }

    /**
     * @return a [Flow] that emits a map of sound ids to their corresponding [SoundPlayer.State] for
     * all sounds managed by the [SoundPlayerManager] instance being used by the
     * [SoundPlaybackService]. If a sound is missing from the map, the callers must assume its state
     * to be [SoundPlayer.State.STOPPED].
     */
    fun getSoundStates(): Flow<Map<String, SoundPlayer.State>> =
      context.bindServiceCallbackFlow<SoundPlaybackService, Binder, Map<String, SoundPlayer.State>> { binder ->
        binder.soundPlayerStatesFlow
      }

    /**
     * @return a [Flow] that emits a map of sound ids to their corresponding volumes for all sounds
     * managed by the [SoundPlayerManager] instance being used by the [SoundPlaybackService]. If a
     * sound is missing from the map, the callers must assume its volume to be `1F`.
     */
    fun getSoundVolumes(): Flow<Map<String, Float>> =
      context.bindServiceCallbackFlow<SoundPlaybackService, Binder, Map<String, Float>> { binder ->
        binder.soundPlayerVolumesFlow
      }

    /**
     * @return a [Flow] that emits the currently playing saved preset in the [SoundPlayerManager]
     * instance being used by the [SoundPlaybackService]. If the currently playing preset is not
     * saved, it emit `null`.
     */
    fun getCurrentPreset(): Flow<Preset?> =
      context.bindServiceCallbackFlow<SoundPlaybackService, Binder, Preset?> { binder ->
        binder.currentPresetFlow
      }

    private inline fun commandSoundPlaybackService(
      foreground: Boolean,
      intentBuilder: Intent.() -> Unit
    ) {
      val intent = Intent(context, SoundPlaybackService::class.java)
      intentBuilder.invoke(intent)
      if (foreground) {
        ContextCompat.startForegroundService(context, intent)
      } else {
        context.startService(intent)
      }
    }

    companion object {
      private const val PREF_SCHEDULED_STOP_MILLIS = "scheduledStopMillis"

      internal const val AUDIO_USAGE_MEDIA = SoundPlaybackService.AUDIO_USAGE_MEDIA
      internal const val AUDIO_USAGE_ALARM = SoundPlaybackService.AUDIO_USAGE_ALARM
    }
  }
}

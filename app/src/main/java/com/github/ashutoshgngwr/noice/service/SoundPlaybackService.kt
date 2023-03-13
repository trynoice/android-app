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
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import androidx.core.os.postDelayed
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.activity.MainActivity
import com.github.ashutoshgngwr.noice.engine.LocalSoundPlayer
import com.github.ashutoshgngwr.noice.engine.MediaPlayer
import com.github.ashutoshgngwr.noice.engine.MediaSessionManager
import com.github.ashutoshgngwr.noice.engine.PlaybackNotificationManager
import com.github.ashutoshgngwr.noice.engine.SoundPlayer
import com.github.ashutoshgngwr.noice.engine.SoundPlayerManager
import com.github.ashutoshgngwr.noice.engine.exoplayer.SoundDataSourceFactory
import com.github.ashutoshgngwr.noice.model.PlayerState
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.provider.CastApiProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.google.android.exoplayer2.upstream.cache.Cache
import com.trynoice.api.client.NoiceApiClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.cast

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

  private val handler = Handler(Looper.getMainLooper())
  private var currentPreset: Preset? = null
  private var presets: List<Preset> = emptyList()
  private val soundPlayerStates = mutableMapOf<String, SoundPlayer.State>()
  private val soundPlayerVolumes = mutableMapOf<String, Float>()

  private val soundPlayerManagerStateFlow = buildMutableSharedFlow(SoundPlayerManager.State.STOPPED)
  private val soundPlayerManagerVolumeFlow = buildMutableSharedFlow(1F)
  private val soundPlayerStatesFlow = buildMutableSharedFlow(soundPlayerStates)
  private val soundPlayerVolumesFlow = buildMutableSharedFlow(soundPlayerVolumes)
  private val currentPresetFlow = buildMutableSharedFlow<Preset?>(null)

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

  private val mediaSessionManager: MediaSessionManager by lazy {
    MediaSessionManager(this, mainActivityPi)
  }

  private val playbackNotificationManager: PlaybackNotificationManager by lazy {
    PlaybackNotificationManager(this, mainActivityPi, mediaSessionManager.getSessionToken())
  }

  private val localDataSourceFactory: SoundDataSourceFactory by lazy {
    SoundDataSourceFactory(apiClient, soundDownloadCache)
  }

  private val localSoundPlayerFactory: SoundPlayer.Factory by lazy {
    LocalSoundPlayer.Factory(
      soundRepository = soundRepository,
      mediaPlayerFactory = MediaPlayer.Factory(this, localDataSourceFactory),
      defaultScope = lifecycleScope,
    )
  }

  private val soundPlayerManager: SoundPlayerManager by lazy {
    SoundPlayerManager(
      context = this,
      soundPlayerFactory = localSoundPlayerFactory,
      listener = this,
    )
  }

  private val wakeLock: PowerManager.WakeLock by lazy {
    requireNotNull(getSystemService<PowerManager>())
      .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "noice:PlaybackService").apply {
        setReferenceCounted(false)
      }
  }

  private val mediaSessionManagerCallback = object : MediaSessionManager.Callback {
    override fun onPlay() {
      soundPlayerManager.resume()
    }

    override fun onStop() {
      soundPlayerManager.stop(false)
    }

    override fun onPause() {
      soundPlayerManager.pause(false)
    }

    override fun onSkipToPrevious() {
      skipPreset(PRESET_SKIP_DIRECTION_PREV)
    }

    override fun onSkipToNext() {
      skipPreset(PRESET_SKIP_DIRECTION_NEXT)
    }
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

    // watch preset repository
    lifecycleScope.launch {
      presetRepository.listFlow().collect { p ->
        presets = p
        // refresh currently playing preset.
        onCurrentPresetChange()
      }
    }

    lifecycleScope.launch {
      subscriptionRepository.isSubscribed().collect { subscribed ->
        localDataSourceFactory.enableDownloadedSounds = subscribed
        soundPlayerManager.setPremiumSegmentsEnabled(subscribed)
      }
    }

    // watch and adapt user settings as they change.
    lifecycleScope.launch {
      settingsRepository.shouldIgnoreAudioFocusChangesAsFlow()
        .collect { soundPlayerManager.setAudioFocusManagementEnabled(!it) }
    }

    lifecycleScope.launch {
      settingsRepository.isMediaButtonsEnabledAsFlow().collect { isEnabled ->
        mediaSessionManager.setCallback(if (isEnabled) mediaSessionManagerCallback else null)
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
        val preset = intent.getSerializableExtraCompat(INTENT_EXTRA_PRESET, Preset::class)
        requireNotNull(preset) { "intent extra '${INTENT_EXTRA_PRESET}' is required to send '${ACTION_PLAY_PRESET}' command" }
        val soundStates = preset.playerStates.associate { it.soundId to (it.volume / 25F) }
        soundPlayerManager.playPreset(soundStates)
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
          mediaSessionManager.setAudioStream(AudioManager.STREAM_MUSIC)
        }
        AUDIO_USAGE_ALARM -> {
          soundPlayerManager.setAudioAttributes(SoundPlayerManager.ALARM_AUDIO_ATTRIBUTES)
          mediaSessionManager.setAudioStream(AudioManager.STREAM_ALARM)
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
            ?.let { p -> p.playerStates.associate { it.soundId to (it.volume / 25F) } }
            ?.also { soundPlayerManager.playPreset(it) }
        }
      }
    }

    return super.onStartCommand(intent, flags, startId)
  }

  override fun onDestroy() {
    Log.d(LOG_TAG, "onDestroy: releasing acquired resources")
    soundPlayerManager.stop(true)
    mediaSessionManager.release()
    castApiProvider.unregisterSessionListener(this)
    unregisterReceiver(becomingNoisyReceiver)
    if (wakeLock.isHeld) {
      wakeLock.release()
    }

    super.onDestroy()
  }

  override fun onCastSessionBegin() {
    Log.d(LOG_TAG, "onCastSessionBegin: switching playback to remote")
    soundPlayerManager.setSoundPlayerFactory(castApiProvider.buildPlayerFactory(this))
    mediaSessionManager.setPlaybackToRemote(castApiProvider.getVolumeProvider())
  }

  override fun onCastSessionEnd() {
    Log.i(LOG_TAG, "onCastSessionEnd: switching playback to local")
    soundPlayerManager.setSoundPlayerFactory(localSoundPlayerFactory)
    mediaSessionManager.setPlaybackToLocal()
  }

  override fun onSoundPlayerManagerStateChange(state: SoundPlayerManager.State) {
    soundPlayerManagerStateFlow.tryEmit(state)
    mediaSessionManager.setPlaybackState(state)
    if (state == SoundPlayerManager.State.STOPPED) {
      Log.d(LOG_TAG, "onSoundPlayerManagerStateChange: playback stopped, releasing resources")
      ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
      wakeLock.release()
    } else {
      Log.d(LOG_TAG, "onSoundPlayerManagerStateChange: playback ongoing, ensuring resources")
      wakeLock.acquire(WAKELOCK_TIMEOUT)
      playbackNotificationManager.createNotification(state, currentPreset)
        .also { startForeground(0x01, it) }
    }
  }

  override fun onSoundPlayerManagerVolumeChange(volume: Float) {
    soundPlayerManagerVolumeFlow.tryEmit(volume)
  }

  override fun onSoundStateChange(soundId: String, state: SoundPlayer.State) {
    soundPlayerStates[soundId] = state
    soundPlayerStatesFlow.tryEmit(soundPlayerStates)
    onCurrentPresetChange()
  }

  override fun onSoundVolumeChange(soundId: String, volume: Float) {
    soundPlayerVolumes[soundId] = volume
    soundPlayerVolumesFlow.tryEmit(soundPlayerVolumes)
    onCurrentPresetChange()
  }

  private fun onCurrentPresetChange() {
    val currentStates = soundPlayerManager.getCurrentPreset()
      .map { PlayerState(it.key, (it.value * 25).roundToInt()) }
      .toTypedArray()

    currentPreset = presets.find { it.hasMatchingPlayerStates(currentStates) }
    mediaSessionManager.setPresetTitle(currentPreset?.name)
    playbackNotificationManager.createNotification(soundPlayerManager.state, currentPreset)
      .also { startForeground(0x01, it) }
  }

  private fun <T> buildMutableSharedFlow(initialValue: T): MutableSharedFlow<T> {
    return MutableSharedFlow<T>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
      .also { it.tryEmit(initialValue) }
  }

  private fun getSoundIdExtra(intent: Intent): String {
    return requireNotNull(intent.getStringExtra(INTENT_EXTRA_SOUND_ID)) {
      "intent extra '${INTENT_EXTRA_SOUND_ID}' is required to send '${intent.action}' command"
    }
  }

  private fun skipPreset(direction: Int) {
    val currentPos = presets.indexOf(currentPreset)
    if (currentPos < 0) { // not playing a saved preset.
      return
    }

    val nextPos = currentPos + direction
    val nextPreset = if (nextPos < 0) {
      presets.last()
    } else if (nextPos >= presets.size) {
      presets.first()
    } else {
      presets[nextPos]
    }

    soundPlayerManager.playPreset(nextPreset.playerStates.associate { it.soundId to (it.volume / 25F) })
  }

  companion object {
    private const val LOG_TAG = "PlaybackService"
    private const val WAKELOCK_TIMEOUT = 24 * 60 * 60 * 1000L

    internal const val ACTION_PLAY_SOUND = "playSound"
    internal const val ACTION_STOP_SOUND = "stopSound"
    internal const val ACTION_PAUSE = "pause"
    internal const val ACTION_RESUME = "resume"
    internal const val ACTION_STOP = "stop"
    internal const val ACTION_PLAY_PRESET = "playPreset"
    internal const val ACTION_SCHEDULE_STOP = "scheduleStop"
    internal const val ACTION_CLEAR_STOP_SCHEDULE = "clearStopSchedule"
    internal const val ACTION_SET_AUDIO_USAGE = "setAudioUsage"
    internal const val ACTION_SKIP_PRESET = "skipPreset"
    internal const val ACTION_SET_VOLUME = "setVolume"
    internal const val ACTION_SET_SOUND_VOLUME = "setSoundVolume"
    internal const val ACTION_PLAY_RANDOM_PRESET = "playRandomPreset"

    internal const val INTENT_EXTRA_SOUND_ID = "soundId"
    internal const val INTENT_EXTRA_PRESET = "preset"
    internal const val INTENT_EXTRA_SCHEDULED_STOP_AT_MILLIS = "scheduledStopAtMillis"
    internal const val INTENT_EXTRA_AUDIO_USAGE = "audioUsage"
    internal const val INTENT_EXTRA_PRESET_SKIP_DIRECTION = "presetSkipDirection"
    internal const val INTENT_EXTRA_VOLUME = "volume"
    internal const val INTENT_EXTRA_SKIP_FADE_TRANSITION = "skipFadeTransition"

    internal const val AUDIO_USAGE_MEDIA = "media"
    internal const val AUDIO_USAGE_ALARM = "alarm"

    internal const val PRESET_SKIP_DIRECTION_NEXT = 1
    internal const val PRESET_SKIP_DIRECTION_PREV = -1

    private const val STOP_CALLBACK_TOKEN = "stopCallback"
  }

  class Binder(
    val soundPlayerManagerStateFlow: Flow<SoundPlayerManager.State>,
    val soundPlayerManagerVolumeFlow: Flow<Float>,
    val soundPlayerStatesFlow: Flow<Map<String, SoundPlayer.State>>,
    val soundPlayerVolumesFlow: Flow<Map<String, Float>>,
    val currentPresetFlow: Flow<Preset?>,
  ) : android.os.Binder()
}

// TODO: remove this extension when a replacement method is available the Androidx Compat libraries.
private fun <T : java.io.Serializable> Intent.getSerializableExtraCompat(
  key: String,
  kClass: KClass<T>
): T? {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getSerializableExtra(key, kClass.java)
  } else {
    @Suppress("DEPRECATION")
    getSerializableExtra(key)?.let { kClass.cast(it) }
  }
}

package com.github.ashutoshgngwr.noice.engine

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.media.AudioAttributesCompat
import com.github.ashutoshgngwr.noice.activity.MainActivity
import com.github.ashutoshgngwr.noice.engine.exoplayer.SoundDataSourceFactory
import com.github.ashutoshgngwr.noice.model.PlayerState
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.CastApiProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.google.android.exoplayer2.upstream.cache.Cache
import com.trynoice.api.client.NoiceApiClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

@AndroidEntryPoint
class PlaybackService : LifecycleService(), PlayerManager.PlaybackListener,
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

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  private var presets = emptyList<Preset>()
  private val playerManagerState = MutableStateFlow(PlaybackState.STOPPED)
  private val playerStates = MutableStateFlow(emptyArray<PlayerState>())
  private val serviceBinder = PlaybackServiceBinder(playerManagerState, playerStates)
  private val isSubscribed: SharedFlow<Boolean> by lazy {
    subscriptionRepository.isSubscribed()
      .shareIn(lifecycleScope, SharingStarted.WhileSubscribed(), 0)
  }

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

  private val localPlayerFactory: Player.Factory by lazy {
    LocalPlayer.Factory(this, localDataSourceFactory)
  }

  private val playerManager: PlayerManager by lazy {
    PlayerManager(
      this,
      settingsRepository.getAudioQuality().bitrate,
      PlayerManager.DEFAULT_AUDIO_ATTRIBUTES,
      soundRepository,
      localPlayerFactory,
      analyticsProvider,
      lifecycleScope,
      this
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
      playerManager.resume()
    }

    override fun onStop() {
      playerManager.stop(false)
    }

    override fun onPause() {
      playerManager.pause()
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
        playerManager.pause(true)
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
      presetRepository.listFlow()
        .flowOn(Dispatchers.IO)
        .collect { p ->
          presets = p
          // refresh currently playing preset and stuff.
          onPlaybackUpdate(playerManagerState.value, playerStates.value)
        }
    }

    lifecycleScope.launch {
      isSubscribed.collect { subscribed ->
        localDataSourceFactory.enableDownloadedSounds = subscribed
        playerManager.setPremiumSegmentsEnabled(subscribed)
      }
    }

    // watch and adapt user settings as they change.
    lifecycleScope.launch {
      settingsRepository.shouldIgnoreAudioFocusChangesAsFlow()
        .flowOn(Dispatchers.IO)
        .collect { playerManager.setAudioFocusManagementEnabled(!it) }
    }

    lifecycleScope.launch {
      settingsRepository.isMediaButtonsEnabledAsFlow()
        .flowOn(Dispatchers.IO)
        .collect { isEnabled ->
          mediaSessionManager.setCallback(if (isEnabled) mediaSessionManagerCallback else null)
        }
    }

    lifecycleScope.launch {
      settingsRepository.getSoundFadeInDurationAsFlow()
        .flowOn(Dispatchers.IO)
        .collect { playerManager.setFadeInDuration(it) }
    }

    lifecycleScope.launch {
      settingsRepository.getSoundFadeOutDurationAsFlow()
        .flowOn(Dispatchers.IO)
        .collect { playerManager.setFadeOutDuration(it) }
    }

    lifecycleScope.launch {
      settingsRepository.getAudioQualityAsFlow()
        .flowOn(Dispatchers.IO)
        .combine(isSubscribed) { quality, subscribed -> if (subscribed) quality else SettingsRepository.FREE_AUDIO_QUALITY }
        .collect { playerManager.setAudioBitrate(it.bitrate) }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_PLAY_SOUND -> playerManager.play(getSoundIdExtra(intent))
      ACTION_STOP_SOUND -> playerManager.stop(getSoundIdExtra(intent))
      ACTION_RESUME -> playerManager.resume()
      ACTION_PAUSE -> playerManager.pause(
        intent.getBooleanExtra(INTENT_EXTRA_SKIP_FADE_TRANSITION, false)
      )
      ACTION_STOP -> playerManager.stop(false)

      ACTION_SET_SOUND_VOLUME -> {
        val volume = intent.getIntExtra(INTENT_EXTRA_SOUND_VOLUME, -1)
        require(volume in 0..Player.MAX_VOLUME) {
          "intent extra '${INTENT_EXTRA_SOUND_VOLUME}=${volume}' must be in range [0, ${Player.MAX_VOLUME}]"
        }

        playerManager.setVolume(getSoundIdExtra(intent), volume)
      }

      ACTION_PLAY_PRESET -> playerManager.play(
        requireNotNull(intent.getSerializableExtra(INTENT_EXTRA_PRESET) as? Preset) {
          "intent extra '${INTENT_EXTRA_PRESET}' is required to send '${ACTION_PLAY_PRESET}' command"
        }
      )

      ACTION_CLEAR_STOP_SCHEDULE -> playerManager.clearStopSchedule()
      ACTION_SCHEDULE_STOP -> {
        val atMillis = intent.getLongExtra(INTENT_EXTRA_SCHEDULED_STOP_AT_MILLIS, -1)
        require(atMillis > 0) {
          "intent extra '${INTENT_EXTRA_SCHEDULED_STOP_AT_MILLIS}' must be a positive long integer"
        }

        playerManager.scheduleStop(atMillis)
      }

      ACTION_SET_AUDIO_USAGE -> when (intent.getIntExtra(INTENT_EXTRA_AUDIO_USAGE, -1)) {
        AudioAttributesCompat.USAGE_MEDIA -> {
          playerManager.setAudioAttributes(PlayerManager.DEFAULT_AUDIO_ATTRIBUTES)
          mediaSessionManager.setAudioStream(AudioManager.STREAM_MUSIC)
        }
        AudioAttributesCompat.USAGE_ALARM -> {
          playerManager.setAudioAttributes(PlayerManager.ALARM_AUDIO_ATTRIBUTES)
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
            .flowOn(Dispatchers.IO)
            .lastOrNull()
            ?.data
            ?.also { playerManager.play(it) }
        }
      }
    }

    return super.onStartCommand(intent, flags, startId)
  }

  override fun onDestroy() {
    Log.d(LOG_TAG, "onDestroy: releasing acquired resources")
    playerManager.stop(true)
    mediaSessionManager.release()
    castApiProvider.unregisterSessionListener(this)
    unregisterReceiver(becomingNoisyReceiver)
    if (wakeLock.isHeld) {
      wakeLock.release()
    }

    super.onDestroy()
  }

  override fun onPlaybackUpdate(
    playerManagerState: PlaybackState,
    playerStates: Array<PlayerState>
  ) {
    Log.i(LOG_TAG, "onPlaybackUpdate: managerState=$playerManagerState")
    val currentPreset = presets.find { it.hasMatchingPlayerStates(playerStates) }
    this.playerManagerState.value = playerManagerState
    this.playerStates.value = playerStates
    mediaSessionManager.setPresetTitle(currentPreset?.name)
    mediaSessionManager.setPlaybackState(playerManagerState)
    if (playerManagerState == PlaybackState.STOPPED) {
      Log.d(LOG_TAG, "onPlaybackUpdate: playback stopped, releasing resources")
      stopForeground(true)
      wakeLock.release()
    } else {
      Log.d(LOG_TAG, "onPlaybackUpdate: playback not stopped, ensuring resource acquisition")
      wakeLock.acquire(WAKELOCK_TIMEOUT)
      playbackNotificationManager.createNotification(playerManagerState, currentPreset)
        .also { startForeground(0x01, it) }
    }
  }

  override fun onCastSessionBegin() {
    Log.d(LOG_TAG, "onCastSessionBegin: switching playback to remote")
    playerManager.setPlayerFactory(castApiProvider.buildPlayerFactory(this))
    mediaSessionManager.setPlaybackToRemote(castApiProvider.getVolumeProvider())
  }

  override fun onCastSessionEnd() {
    Log.i(LOG_TAG, "onCastSessionEnd: switching playback to local")
    playerManager.setPlayerFactory(localPlayerFactory)
    mediaSessionManager.setPlaybackToLocal()
  }

  private fun getSoundIdExtra(intent: Intent): String {
    return requireNotNull(intent.getStringExtra(INTENT_EXTRA_SOUND_ID)) {
      "intent extra '${INTENT_EXTRA_SOUND_ID}' is required to send '${intent.action}' command"
    }
  }

  private fun skipPreset(direction: Int) {
    val currentPos = presets.indexOfFirst { it.hasMatchingPlayerStates(playerStates.value) }
    if (currentPos < 0) { // not playing a saved preset.
      return
    }

    val nextPos = currentPos + direction
    playerManager.play(
      if (nextPos < 0) {
        presets.last()
      } else if (nextPos >= presets.size) {
        presets.first()
      } else {
        presets[nextPos]
      }
    )
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
    internal const val ACTION_SET_SOUND_VOLUME = "setSoundVolume"
    internal const val ACTION_PLAY_RANDOM_PRESET = "playRandomPreset"

    internal const val INTENT_EXTRA_SOUND_ID = "soundId"
    internal const val INTENT_EXTRA_PRESET = "preset"
    internal const val INTENT_EXTRA_SCHEDULED_STOP_AT_MILLIS = "scheduledStopAtMillis"
    internal const val INTENT_EXTRA_AUDIO_USAGE = "audioUsage"
    internal const val INTENT_EXTRA_PRESET_SKIP_DIRECTION = "presetSkipDirection"
    internal const val INTENT_EXTRA_SOUND_VOLUME = "volume"
    internal const val INTENT_EXTRA_SKIP_FADE_TRANSITION = "skipFadeTransition"

    internal const val PRESET_SKIP_DIRECTION_NEXT = PlayerManager.PRESET_SKIP_DIRECTION_NEXT
    internal const val PRESET_SKIP_DIRECTION_PREV = PlayerManager.PRESET_SKIP_DIRECTION_PREV
  }
}

class PlaybackServiceBinder(
  val playerManagerState: Flow<PlaybackState>,
  val playerStates: Flow<Array<PlayerState>>,
) : Binder()

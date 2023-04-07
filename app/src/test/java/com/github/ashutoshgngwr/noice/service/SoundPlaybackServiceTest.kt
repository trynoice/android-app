package com.github.ashutoshgngwr.noice.service

import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.media.VolumeProvider
import androidx.media.VolumeProviderCompat
import com.github.ashutoshgngwr.noice.cast.CastReceiverUiManager
import com.github.ashutoshgngwr.noice.di.ApiClientModule
import com.github.ashutoshgngwr.noice.di.CastApiProviderModule
import com.github.ashutoshgngwr.noice.engine.AudioFocusManager
import com.github.ashutoshgngwr.noice.engine.LocalSoundPlayer
import com.github.ashutoshgngwr.noice.engine.SoundPlaybackNotificationManager
import com.github.ashutoshgngwr.noice.engine.SoundPlayer
import com.github.ashutoshgngwr.noice.engine.SoundPlayerManager
import com.github.ashutoshgngwr.noice.engine.SoundPlayerManagerMediaSession
import com.github.ashutoshgngwr.noice.engine.exoplayer.SoundDataSourceFactory
import com.github.ashutoshgngwr.noice.models.Preset
import com.github.ashutoshgngwr.noice.provider.CastApiProvider
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.trynoice.api.client.NoiceApiClient
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowPowerManager
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@HiltAndroidTest
@UninstallModules(
  CastApiProviderModule::class,
  ApiClientModule::class,
)
@RunWith(RobolectricTestRunner::class)
class SoundPlaybackServiceTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @BindValue
  internal lateinit var presetRepositoryMock: PresetRepository

  @BindValue
  internal lateinit var soundRepositoryMock: SoundRepository

  @BindValue
  internal lateinit var subscriptionRepositoryMock: SubscriptionRepository

  @BindValue
  internal lateinit var settingsRepositoryMock: SettingsRepository

  @BindValue
  internal lateinit var apiClientMock: NoiceApiClient

  @BindValue
  internal lateinit var castApiProviderMock: CastApiProvider

  @Before
  fun setUp() {
    mockkConstructor(
      AudioFocusManager::class,
      SoundDataSourceFactory::class,
      SoundPlaybackNotificationManager::class,
      SoundPlayerManager::class,
      SoundPlayerManagerMediaSession::class,
    )

    presetRepositoryMock = mockk(relaxed = true)
    soundRepositoryMock = mockk(relaxed = true)
    subscriptionRepositoryMock = mockk(relaxed = true)
    settingsRepositoryMock = mockk(relaxed = true)
    apiClientMock = mockk(relaxed = true)
    castApiProviderMock = mockk(relaxed = true) {
      // default mocks for the compat implementation doesn't work
      every { getVolumeProvider() } returns mockk(relaxed = true) {
        every { volumeProvider } returns mockk<VolumeProvider>(relaxed = true)
      }
    }
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun service_becomingNoisyReceiver() {
    every { anyConstructed<SoundPlayerManager>().pause(any()) } returns Unit
    val (service) = buildServiceAndController()
    service.sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    ShadowLooper.idleMainLooper()
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().pause(any()) }
  }

  @Test
  fun service_premiumStatusChange() {
    val isSubscribedStateFlow = MutableStateFlow(false)
    every { subscriptionRepositoryMock.isSubscribed() } returns isSubscribedStateFlow
    every { settingsRepositoryMock.getAudioQualityAsFlow() } returns flowOf(SettingsRepository.AudioQuality.HIGH)
    every { anyConstructed<SoundPlayerManager>().setPremiumSegmentsEnabled(any()) } returns Unit
    every { anyConstructed<SoundDataSourceFactory>() setProperty "enableDownloadedSounds" value any<Boolean>() } returns Unit
    every { anyConstructed<SoundPlayerManager>().setAudioBitrate(any()) } returns Unit
    val (_) = buildServiceAndController()
    verify(exactly = 1) {
      anyConstructed<SoundPlayerManager>().setPremiumSegmentsEnabled(false)
      anyConstructed<SoundDataSourceFactory>() setProperty "enableDownloadedSounds" value false
      anyConstructed<SoundPlayerManager>().setAudioBitrate(SettingsRepository.FREE_AUDIO_QUALITY.bitrate)
    }

    isSubscribedStateFlow.value = true
    verify(exactly = 1) {
      anyConstructed<SoundPlayerManager>().setPremiumSegmentsEnabled(true)
      anyConstructed<SoundDataSourceFactory>() setProperty "enableDownloadedSounds" value true
      anyConstructed<SoundPlayerManager>().setAudioBitrate(SettingsRepository.AudioQuality.HIGH.bitrate)
    }
  }

  @Test
  fun service_audioFocusManagement() {
    val ignoreAudioFocusStateFlow = MutableStateFlow(false)
    every { settingsRepositoryMock.shouldIgnoreAudioFocusChangesAsFlow() } returns ignoreAudioFocusStateFlow
    every { anyConstructed<AudioFocusManager>().setDisabled(any()) } returns Unit

    val (_) = buildServiceAndController()
    verify(exactly = 1) { anyConstructed<AudioFocusManager>().setDisabled(false) }

    ignoreAudioFocusStateFlow.value = true
    verify(exactly = 1) { anyConstructed<AudioFocusManager>().setDisabled(true) }
  }

  @Test
  fun service_mediaButtons() {
    val enableMediaButtonsStateFlow = MutableStateFlow(false)
    every { settingsRepositoryMock.isMediaButtonsEnabledAsFlow() } returns enableMediaButtonsStateFlow
    every { anyConstructed<SoundPlayerManager>().pause(any()) } returns Unit
    every { anyConstructed<SoundPlayerManager>().stop(any()) } returns Unit
    every { anyConstructed<SoundPlayerManager>().resume() } returns Unit
    every { anyConstructed<SoundPlayerManager>().playPreset(any()) } returns Unit

    var sessionCallback: SoundPlayerManagerMediaSession.Callback? = null
    every { anyConstructed<SoundPlayerManagerMediaSession>().setCallback(any()) } answers {
      sessionCallback = firstArg()
    }

    val (service) = buildServiceAndController()
    assertNull(sessionCallback)

    enableMediaButtonsStateFlow.value = true
    assertNotNull(sessionCallback)

    sessionCallback?.onPause()
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().pause(any()) }

    sessionCallback?.onPlay()
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().resume() }

    sessionCallback?.onStop()
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().stop(any()) }

    val currentPreset = Preset("current-preset", sortedMapOf("sound-1" to 0.8F))
    every { anyConstructed<SoundPlayerManager>().getCurrentPreset() } returns currentPreset.soundStates
    coEvery { presetRepositoryMock.getBySoundStatesFlow(any()) } returns flowOf(currentPreset)
    service.onCurrentPresetChange()

    val nextPreset = Preset("next-preset", sortedMapOf("sound-2" to 1F))
    coEvery { presetRepositoryMock.getNextPreset(any()) } returns nextPreset
    sessionCallback?.onSkipToNext()
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().playPreset(nextPreset.soundStates) }

    val prevPreset = Preset("prev-preset", sortedMapOf("sound-6" to 0F))
    coEvery { presetRepositoryMock.getPreviousPreset(any()) } returns prevPreset
    sessionCallback?.onSkipToPrevious()
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().playPreset(prevPreset.soundStates) }
  }

  @Test
  fun service_soundFadeDurations() {
    val fadeInDurationStateFlow = MutableStateFlow(Duration.ZERO)
    val fadeOutDurationStateFlow = MutableStateFlow(Duration.ZERO)
    every { settingsRepositoryMock.getSoundFadeInDurationAsFlow() } returns fadeInDurationStateFlow
    every { settingsRepositoryMock.getSoundFadeOutDurationAsFlow() } returns fadeOutDurationStateFlow
    every { anyConstructed<SoundPlayerManager>().setFadeInDuration(any()) } returns Unit
    every { anyConstructed<SoundPlayerManager>().setFadeOutDuration(any()) } returns Unit

    val (_) = buildServiceAndController()
    verify(exactly = 1) {
      anyConstructed<SoundPlayerManager>().setFadeInDuration(Duration.ZERO)
      anyConstructed<SoundPlayerManager>().setFadeOutDuration(Duration.ZERO)
    }

    fadeInDurationStateFlow.value = 2.minutes
    fadeOutDurationStateFlow.value = 4.minutes
    verify(exactly = 1) {
      anyConstructed<SoundPlayerManager>().setFadeInDuration(2.minutes)
      anyConstructed<SoundPlayerManager>().setFadeOutDuration(4.minutes)
    }
  }

  @Test
  fun service_audioQuality() {
    val audioQualityStateFlow = MutableStateFlow(SettingsRepository.AudioQuality.LOW)
    every { settingsRepositoryMock.getAudioQualityAsFlow() } returns audioQualityStateFlow
    every { subscriptionRepositoryMock.isSubscribed() } returns flowOf(true)
    every { anyConstructed<SoundPlayerManager>().setAudioBitrate(any()) } returns Unit

    val (_) = buildServiceAndController()

    listOf(
      SettingsRepository.AudioQuality.LOW,
      SettingsRepository.AudioQuality.MEDIUM,
      SettingsRepository.AudioQuality.HIGH,
      SettingsRepository.AudioQuality.ULTRA_HIGH,
    ).forEach { audioQuality ->
      audioQualityStateFlow.value = audioQuality
      ShadowLooper.idleMainLooper()
      verify(exactly = 1) { anyConstructed<SoundPlayerManager>().setAudioBitrate(audioQuality.bitrate) }
    }
  }

  @Test
  fun service_castSession() {
    val castReceiverUiManagerMock = mockk<CastReceiverUiManager>(relaxed = true)
    val castSoundPlayerFactoryMock = mockk<SoundPlayer.Factory>(relaxed = true)


    every { castApiProviderMock.getReceiverUiManager() } returns castReceiverUiManagerMock
    every { castApiProviderMock.getSoundPlayerFactory() } returns castSoundPlayerFactoryMock

    lateinit var castSessionListener: CastApiProvider.SessionListener
    every { castApiProviderMock.registerSessionListener(any()) } answers {
      castSessionListener = firstArg()
    }

    val (service) = buildServiceAndController()
    every { anyConstructed<SoundPlayerManager>().setSoundPlayerFactory(any()) } returns Unit
    castSessionListener.onCastSessionBegin()
    verify(exactly = 1) {
      anyConstructed<SoundPlayerManager>().setSoundPlayerFactory(castSoundPlayerFactoryMock)
    }

    listOf(
      Pair(SoundPlayerManager.State.PLAYING, 0.2F),
      Pair(SoundPlayerManager.State.PAUSING, 0.4F),
      Pair(SoundPlayerManager.State.PAUSED, 0.6F),
      Pair(SoundPlayerManager.State.STOPPING, 0.8F),
      Pair(SoundPlayerManager.State.STOPPED, 1F),
    ).forEach { (managerState, managerVolume) ->
      clearMocks(castReceiverUiManagerMock)
      service.onSoundPlayerManagerStateChange(managerState)
      service.onSoundPlayerManagerVolumeChange(managerVolume)
      verify(atLeast = 1) {
        castReceiverUiManagerMock.setSoundPlayerManagerState(managerState, managerVolume)
      }
    }

    listOf(
      Triple("sound-1", SoundPlayer.State.BUFFERING, 0F),
      Triple("sound-2", SoundPlayer.State.PLAYING, 0.2F),
      Triple("sound-3", SoundPlayer.State.PAUSING, 0.4F),
      Triple("sound-4", SoundPlayer.State.PAUSED, 0.6F),
      Triple("sound-5", SoundPlayer.State.STOPPING, 0.8F),
      Triple("sound-6", SoundPlayer.State.STOPPED, 1F),
    ).forEach { (soundId, soundState, soundVolume) ->
      clearMocks(castReceiverUiManagerMock)
      service.onSoundStateChange(soundId, soundState)
      service.onSoundVolumeChange(soundId, soundVolume)
      verify(atLeast = 1) {
        castReceiverUiManagerMock.setSoundPlayerState(soundId, soundState, soundVolume)
      }
    }

    listOf(
      Pair(Preset("test-preset-1", sortedMapOf("sound-1" to 1F, "sound-2" to 0.8F)), true),
      Pair(Preset("test-preset-2", sortedMapOf("sound-3" to 0.6F, "sound-4" to 0.4F)), false),
      Pair(Preset("test-preset-3", sortedMapOf("sound-5" to 0.2F, "sound-6" to 0F)), true),
    ).forEach { (preset, isSaved) ->
      clearMocks(castReceiverUiManagerMock, presetRepositoryMock)
      every { anyConstructed<SoundPlayerManager>().getCurrentPreset() } returns preset.soundStates
      every {
        presetRepositoryMock.getBySoundStatesFlow(preset.soundStates)
      } answers { flowOf(if (isSaved) preset else null) }

      service.onCurrentPresetChange()
      verify(atLeast = 1) { castReceiverUiManagerMock.setPresetName(if (isSaved) preset.name else null) }
    }

    castSessionListener.onCastSessionEnd()
    verify(atLeast = 1) {
      anyConstructed<SoundPlayerManager>().setSoundPlayerFactory(ofType(LocalSoundPlayer.Factory::class))
    }
  }

  @Test
  fun service_mediaSession() {
    lateinit var castSessionListener: CastApiProvider.SessionListener
    every { castApiProviderMock.registerSessionListener(any()) } answers {
      castSessionListener = firstArg()
    }

    every { anyConstructed<SoundPlayerManagerMediaSession>().setCurrentPresetName(any()) } returns Unit
    val (service) = buildServiceAndController()
    listOf(
      Pair(Preset("test-preset-1", sortedMapOf("sound-1" to 1F, "sound-2" to 0.8F)), true),
      Pair(Preset("test-preset-2", sortedMapOf("sound-3" to 0.6F, "sound-4" to 0.4F)), false),
      Pair(Preset("test-preset-3", sortedMapOf("sound-5" to 0.2F, "sound-6" to 0F)), true),
    ).forEach { (preset, isSaved) ->
      every { anyConstructed<SoundPlayerManager>().getCurrentPreset() } returns preset.soundStates
      every {
        presetRepositoryMock.getBySoundStatesFlow(preset.soundStates)
      } answers { flowOf(if (isSaved) preset else null) }

      service.onCurrentPresetChange()
      verify(atLeast = 1) {
        anyConstructed<SoundPlayerManagerMediaSession>()
          .setCurrentPresetName(if (isSaved) preset.name else null)
      }
    }

    val castVolumeProviderMock = mockk<VolumeProviderCompat>(relaxed = true) {
      every { volumeProvider } returns mockk<VolumeProvider>(relaxed = true)
    }

    every { castApiProviderMock.getVolumeProvider() } returns castVolumeProviderMock
    every { anyConstructed<SoundPlayerManagerMediaSession>().setPlaybackToRemote(any()) } returns Unit
    every { anyConstructed<SoundPlayerManagerMediaSession>().setPlaybackToLocal() } returns Unit
    castSessionListener.onCastSessionBegin()
    castSessionListener.onCastSessionEnd()
    verifyOrder {
      anyConstructed<SoundPlayerManagerMediaSession>().setPlaybackToRemote(castVolumeProviderMock)
      anyConstructed<SoundPlayerManagerMediaSession>().setPlaybackToLocal()
    }
  }

  @Test
  fun service_notification() {
    every { anyConstructed<SoundPlaybackNotificationManager>().setCurrentPresetName(any()) } returns Unit
    val (service) = buildServiceAndController()
    listOf(
      Pair(Preset("test-preset-1", sortedMapOf("sound-1" to 1F, "sound-2" to 0.8F)), true),
      Pair(Preset("test-preset-2", sortedMapOf("sound-3" to 0.6F, "sound-4" to 0.4F)), false),
      Pair(Preset("test-preset-3", sortedMapOf("sound-5" to 0.2F, "sound-6" to 0F)), true),
    ).forEach { (preset, isSaved) ->
      every { anyConstructed<SoundPlayerManager>().getCurrentPreset() } returns preset.soundStates
      every {
        presetRepositoryMock.getBySoundStatesFlow(preset.soundStates)
      } answers { flowOf(if (isSaved) preset else null) }

      service.onCurrentPresetChange()
      verify(atLeast = 1) {
        anyConstructed<SoundPlaybackNotificationManager>()
          .setCurrentPresetName(if (isSaved) preset.name else null)
      }
    }
  }

  @Test
  fun service_wakeLock() {
    val (service) = buildServiceAndController()
    service.onSoundPlayerManagerStateChange(SoundPlayerManager.State.PLAYING)
    val wakeLock = ShadowPowerManager.getLatestWakeLock()
    assertTrue(wakeLock.isHeld)

    service.onSoundPlayerManagerStateChange(SoundPlayerManager.State.STOPPED)
    assertFalse(wakeLock.isHeld)
  }

  @Test
  fun service_idleTimeout() {
    every { anyConstructed<SoundPlayerManager>().stop(any()) } returns Unit
    val (service) = buildServiceAndController()

    service.onSoundPlayerManagerStateChange(SoundPlayerManager.State.PAUSED)
    ShadowLooper.idleMainLooper(5, TimeUnit.MINUTES)
    verify(exactly = 0) { anyConstructed<SoundPlayerManager>().stop(any()) }

    service.onSoundPlayerManagerStateChange(SoundPlayerManager.State.PLAYING)
    ShadowLooper.idleMainLooper(10, TimeUnit.MINUTES)
    verify(exactly = 0) { anyConstructed<SoundPlayerManager>().stop(any()) }

    service.onSoundPlayerManagerStateChange(SoundPlayerManager.State.PAUSED)
    ShadowLooper.idleMainLooper(5, TimeUnit.MINUTES)
    verify(exactly = 0) { anyConstructed<SoundPlayerManager>().stop(any()) }

    ShadowLooper.idleMainLooper(5, TimeUnit.MINUTES)
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().stop(any()) }
  }

  @Test
  fun controller_playSound() {
    every { anyConstructed<SoundPlayerManager>().playSound(any()) } returns Unit
    val (_, controller) = buildServiceAndController()
    controller.playSound("test-sound-id")
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().playSound("test-sound-id") }
  }

  @Test
  fun controller_stopSound() {
    every { anyConstructed<SoundPlayerManager>().stopSound(any()) } returns Unit
    val (_, controller) = buildServiceAndController()
    controller.stopSound("test-sound-id")
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().stopSound("test-sound-id") }
  }

  @Test
  fun controller_setVolume() {
    every { anyConstructed<SoundPlayerManager>().setVolume(any()) } returns Unit
    val (_, controller) = buildServiceAndController()
    controller.setVolume(0.4F)
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().setVolume(0.4F) }
  }

  @Test
  fun controller_setSoundVolume() {
    every { anyConstructed<SoundPlayerManager>().setSoundVolume(any(), any()) } returns Unit
    val (_, controller) = buildServiceAndController()
    controller.setSoundVolume("test-sound-id", 0.3F)
    verify(exactly = 1) {
      anyConstructed<SoundPlayerManager>().setSoundVolume("test-sound-id", 0.3F)
    }
  }

  @Test
  fun controller_pause() {
    every { anyConstructed<SoundPlayerManager>().pause(any()) } returns Unit
    val (_, controller) = buildServiceAndController()
    listOf(true, false).forEach { immediate ->
      controller.pause(immediate)
      verify(exactly = 1) { anyConstructed<SoundPlayerManager>().pause(immediate) }
    }
  }

  @Test
  fun controller_resume() {
    every { anyConstructed<SoundPlayerManager>().resume() } returns Unit
    val (_, controller) = buildServiceAndController()
    controller.resume()
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().resume() }
  }

  @Test
  fun controller_stop() {
    every { anyConstructed<SoundPlayerManager>().stop(any()) } returns Unit
    val (_, controller) = buildServiceAndController()
    controller.stop()
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().stop(any()) }
  }

  @Test
  fun controller_playPreset() {
    every { anyConstructed<SoundPlayerManager>().playPreset(any()) } returns Unit
    val (_, controller) = buildServiceAndController()
    val testPreset = Preset("test-preset", sortedMapOf("sound-1" to 1F, "sound-2" to 0.8F))
    controller.playPreset(testPreset)
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().playPreset(testPreset.soundStates) }
  }

  @Test
  fun controller_scheduleStop_getStopScheduleRemainingMillis() {
    every { anyConstructed<SoundPlayerManager>().pause(any()) } returns Unit

    val (_, controller) = buildServiceAndController()
    controller.scheduleStop(30000L)
    verify(exactly = 0) { anyConstructed<SoundPlayerManager>().pause(any()) }

    // update schedule
    controller.scheduleStop(60000L)

    val remainingMillis = controller.getStopScheduleRemainingMillis()
    assertTrue(remainingMillis in 55000..60000L)

    ShadowLooper.idleMainLooper(50, TimeUnit.SECONDS)
    verify(exactly = 0) { anyConstructed<SoundPlayerManager>().pause(any()) }

    ShadowLooper.idleMainLooper(15, TimeUnit.SECONDS)
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().pause(any()) }
  }

  @Test
  fun controller_clearStopSchedule_getStopScheduleRemainingMillis() {
    every { anyConstructed<SoundPlayerManager>().pause(any()) } returns Unit

    val (_, controller) = buildServiceAndController()
    controller.scheduleStop(60000L)
    ShadowLooper.idleMainLooper(50, TimeUnit.SECONDS)

    controller.clearStopSchedule()
    assertEquals(0, controller.getStopScheduleRemainingMillis())
    verify(exactly = 0) { anyConstructed<SoundPlayerManager>().pause(any()) }

    ShadowLooper.idleMainLooper(15, TimeUnit.SECONDS)
    verify(exactly = 0) { anyConstructed<SoundPlayerManager>().pause(any()) }
  }

  @Test
  fun controller_setAudioUsage() {
    every { anyConstructed<SoundPlayerManager>().setAudioAttributes(any()) } returns Unit
    every { anyConstructed<SoundPlayerManagerMediaSession>().setAudioStream(any()) } returns Unit
    val (_, controller) = buildServiceAndController()
    mapOf(
      SoundPlaybackService.Controller.AUDIO_USAGE_ALARM to SoundPlayerManager.ALARM_AUDIO_ATTRIBUTES,
      SoundPlaybackService.Controller.AUDIO_USAGE_MEDIA to SoundPlayerManager.DEFAULT_AUDIO_ATTRIBUTES,
    ).forEach { (usage, attrs) ->
      controller.setAudioUsage(usage)
      verify(exactly = 1) {
        anyConstructed<SoundPlayerManager>().setAudioAttributes(attrs)
        anyConstructed<SoundPlayerManagerMediaSession>().setAudioStream(attrs.legacyStreamType)
      }
    }
  }

  @Test
  fun controller_saveCurrentPreset() {
    val soundStates = sortedMapOf(
      "sound-1" to 1F,
      "sound-2" to 0.8F,
      "sound-3" to 0.5F,
    )

    every { anyConstructed<SoundPlayerManager>().getCurrentPreset() } returns soundStates
    val (_, controller) = buildServiceAndController()
    controller.saveCurrentPreset("test-preset-name")
    coVerify(exactly = 1) {
      presetRepositoryMock.save(withArg { preset ->
        assertEquals(soundStates, preset.soundStates)
      })
    }
  }

  @Test
  fun controller_getState() = runTest {
    val (service, controller) = buildServiceAndController()
    listOf(
      SoundPlayerManager.State.PLAYING,
      SoundPlayerManager.State.PAUSING,
      SoundPlayerManager.State.PAUSED,
      SoundPlayerManager.State.STOPPING,
      SoundPlayerManager.State.STOPPED,
    ).forEach { state ->
      service.onSoundPlayerManagerStateChange(state)
      assertEquals(state, controller.getState().firstOrNull())
    }
  }

  @Test
  fun controller_getVolume() = runTest {
    val (service, controller) = buildServiceAndController()
    listOf(0.2F, 0.4F, 0.6F, 0.8F, 1F).forEach { volume ->
      service.onSoundPlayerManagerVolumeChange(volume)
      assertEquals(volume, controller.getVolume().firstOrNull())
    }
  }

  @Test
  fun controller_getSoundStates() = runTest {
    val states = mapOf(
      "sound-1" to SoundPlayer.State.BUFFERING,
      "sound-2" to SoundPlayer.State.PLAYING,
      "sound-3" to SoundPlayer.State.PAUSING,
      "sound-4" to SoundPlayer.State.PAUSED,
      "sound-5" to SoundPlayer.State.STOPPING,
      "sound-6" to SoundPlayer.State.STOPPED,
    )

    val (service, controller) = buildServiceAndController()
    states.forEach { (soundId, state) ->
      service.onSoundStateChange(soundId, state)
    }

    states.forEach { (soundId, expectedState) ->
      assertEquals(expectedState, controller.getSoundStates().firstOrNull()?.get(soundId))
    }
  }

  @Test
  fun controller_getSoundVolumes() = runTest {
    val volumes = mapOf(
      "sound-1" to 1F,
      "sound-2" to 0.8F,
      "sound-3" to 0.6F,
      "sound-4" to 0.4F,
      "sound-5" to 0.2F,
      "sound-6" to 0F,
    )

    val (service, controller) = buildServiceAndController()
    volumes.forEach { (soundId, volume) ->
      service.onSoundVolumeChange(soundId, volume)
    }

    volumes.forEach { (soundId, expectedVolume) ->
      assertEquals(expectedVolume, controller.getSoundVolumes().firstOrNull()?.get(soundId))
    }
  }

  @Test
  fun controller_getCurrentPreset() = runTest {
    val (service, controller) = buildServiceAndController()
    listOf(
      Pair(Preset("test-preset-1", sortedMapOf("sound-1" to 1F, "sound-2" to 0.8F)), false),
      Pair(Preset("test-preset-2", sortedMapOf("sound-3" to 0.6F, "sound-4" to 0.4F)), true),
      Pair(Preset("test-preset-3", sortedMapOf("sound-5" to 0.2F, "sound-6" to 0F)), true),
    ).forEach { (preset, isSaved) ->
      every { anyConstructed<SoundPlayerManager>().getCurrentPreset() } returns preset.soundStates
      every {
        presetRepositoryMock.getBySoundStatesFlow(preset.soundStates)
      } returns flowOf(if (isSaved) preset else null)

      service.onCurrentPresetChange()
      assertEquals(if (isSaved) preset else null, controller.getCurrentPreset().firstOrNull())
    }
  }

  private fun buildServiceAndController(): Pair<SoundPlaybackService, SoundPlaybackService.Controller> {
    val serviceName = SoundPlaybackService::class.qualifiedName
    val service = Robolectric.buildService(SoundPlaybackService::class.java).create().bind().get()
    val controller = SoundPlaybackService.Controller(spyk(service) {
      every { startService(match { it.component?.className == serviceName }) } answers {
        onStartCommand(firstArg(), 0, 0)
        firstArg<Intent>().component
      }

      every { startForegroundService(match { it.component?.className == serviceName }) } answers {
        onStartCommand(firstArg(), 0, 0)
        firstArg<Intent>().component
      }

      every {
        bindService(match { it.component?.className == serviceName }, any(), any())
      } answers {
        val intent = firstArg<Intent>()
        secondArg<ServiceConnection>().onServiceConnected(intent.component, onBind(intent))
        true
      }
    })

    return Pair(service, controller)
  }
}

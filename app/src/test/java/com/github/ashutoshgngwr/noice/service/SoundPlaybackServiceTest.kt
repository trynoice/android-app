package com.github.ashutoshgngwr.noice.service

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.di.ApiClientModule
import com.github.ashutoshgngwr.noice.di.CastApiProviderModule
import com.github.ashutoshgngwr.noice.engine.SoundPlayer
import com.github.ashutoshgngwr.noice.engine.SoundPlayerManager
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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
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
import java.util.*
import java.util.concurrent.TimeUnit

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

  private lateinit var service: SoundPlaybackService
  private lateinit var controller: SoundPlaybackService.Controller

  @Before
  fun setUp() {
    mockkConstructor(SoundPlayerManager::class)

    presetRepositoryMock = mockk(relaxed = true)
    soundRepositoryMock = mockk(relaxed = true)
    subscriptionRepositoryMock = mockk(relaxed = true)
    settingsRepositoryMock = mockk(relaxed = true)
    apiClientMock = mockk(relaxed = true)
    castApiProviderMock = mockk(relaxed = true)

    val context = ApplicationProvider.getApplicationContext<Context>()
    service = Robolectric.buildService(SoundPlaybackService::class.java).create().bind().get()
    val serviceName = SoundPlaybackService::class.qualifiedName
    controller = SoundPlaybackService.Controller(spyk(context) {
      every { startService(match { it.component?.className == serviceName }) } answers {
        service.onStartCommand(firstArg(), 0, 0)
        firstArg<Intent>().component
      }

      every { startForegroundService(match { it.component?.className == serviceName }) } answers {
        service.onStartCommand(firstArg(), 0, 0)
        firstArg<Intent>().component
      }

      every {
        bindService(match { it.component?.className == serviceName }, any(), any())
      } answers {
        val intent = firstArg<Intent>()
        secondArg<ServiceConnection>().onServiceConnected(intent.component, service.onBind(intent))
        true
      }
    })
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun playSound() {
    every { anyConstructed<SoundPlayerManager>().playSound(any()) } returns Unit
    controller.playSound("test-sound-id")
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().playSound("test-sound-id") }
  }

  @Test
  fun stopSound() {
    every { anyConstructed<SoundPlayerManager>().stopSound(any()) } returns Unit
    controller.stopSound("test-sound-id")
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().stopSound("test-sound-id") }
  }

  @Test
  fun setVolume() {
    every { anyConstructed<SoundPlayerManager>().setVolume(any()) } returns Unit
    controller.setVolume(0.4F)
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().setVolume(0.4F) }
  }

  @Test
  fun setSoundVolume() {
    every { anyConstructed<SoundPlayerManager>().setSoundVolume(any(), any()) } returns Unit
    controller.setSoundVolume("test-sound-id", 0.3F)
    verify(exactly = 1) {
      anyConstructed<SoundPlayerManager>().setSoundVolume("test-sound-id", 0.3F)
    }
  }

  @Test
  fun pause() {
    every { anyConstructed<SoundPlayerManager>().pause(any()) } returns Unit
    listOf(true, false).forEach { immediate ->
      controller.pause(immediate)
      verify(exactly = 1) { anyConstructed<SoundPlayerManager>().pause(immediate) }
    }
  }

  @Test
  fun resume() {
    every { anyConstructed<SoundPlayerManager>().resume() } returns Unit
    controller.resume()
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().resume() }
  }

  @Test
  fun stop() {
    every { anyConstructed<SoundPlayerManager>().stop(any()) } returns Unit
    controller.stop()
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().stop(any()) }
  }

  @Test
  fun playPreset() {
    every { anyConstructed<SoundPlayerManager>().playPreset(any()) } returns Unit
    val testPreset = Preset("test-preset", sortedMapOf("sound-1" to 1F, "sound-2" to 0.8F))
    controller.playPreset(testPreset)
    verify(exactly = 1) { anyConstructed<SoundPlayerManager>().playPreset(testPreset.soundStates) }
  }

  @Test
  fun scheduleStop_getStopScheduleRemainingMillis() {
    every { anyConstructed<SoundPlayerManager>().pause(any()) } returns Unit

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
  fun clearStopSchedule_getStopScheduleRemainingMillis() {
    every { anyConstructed<SoundPlayerManager>().pause(any()) } returns Unit

    controller.scheduleStop(60000L)
    ShadowLooper.idleMainLooper(50, TimeUnit.SECONDS)

    controller.clearStopSchedule()
    assertEquals(0, controller.getStopScheduleRemainingMillis())
    verify(exactly = 0) { anyConstructed<SoundPlayerManager>().pause(any()) }

    ShadowLooper.idleMainLooper(15, TimeUnit.SECONDS)
    verify(exactly = 0) { anyConstructed<SoundPlayerManager>().pause(any()) }
  }

  @Test
  fun setAudioUsage() {
    every { anyConstructed<SoundPlayerManager>().setAudioAttributes(any()) } returns Unit
    mapOf(
      SoundPlaybackService.Controller.AUDIO_USAGE_ALARM to SoundPlayerManager.ALARM_AUDIO_ATTRIBUTES,
      SoundPlaybackService.Controller.AUDIO_USAGE_MEDIA to SoundPlayerManager.DEFAULT_AUDIO_ATTRIBUTES,
    ).forEach { (usage, attrs) ->
      controller.setAudioUsage(usage)
      verify(exactly = 1) { anyConstructed<SoundPlayerManager>().setAudioAttributes(attrs) }
    }
  }

  @Test
  fun saveCurrentPreset() {
    val soundStates = sortedMapOf(
      "sound-1" to 1F,
      "sound-2" to 0.8F,
      "sound-3" to 0.5F,
    )

    every { anyConstructed<SoundPlayerManager>().getCurrentPreset() } returns soundStates
    controller.saveCurrentPreset("test-preset-name")
    coVerify(exactly = 1) {
      presetRepositoryMock.save(withArg { preset ->
        assertEquals(soundStates, preset.soundStates)
      })
    }
  }

  @Test
  fun getState() = runTest {
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
  fun getVolume() = runTest {
    listOf(0.2F, 0.4F, 0.6F, 0.8F, 1F).forEach { volume ->
      service.onSoundPlayerManagerVolumeChange(volume)
      assertEquals(volume, controller.getVolume().firstOrNull())
    }
  }

  @Test
  fun getSoundStates() = runTest {
    val states = mapOf(
      "sound-1" to SoundPlayer.State.BUFFERING,
      "sound-2" to SoundPlayer.State.PLAYING,
      "sound-3" to SoundPlayer.State.PAUSING,
      "sound-4" to SoundPlayer.State.PAUSED,
      "sound-5" to SoundPlayer.State.STOPPING,
      "sound-6" to SoundPlayer.State.STOPPED,
    )

    states.forEach { (soundId, state) ->
      service.onSoundStateChange(soundId, state)
    }

    assertEquals(states, controller.getSoundStates().firstOrNull())
  }

  @Test
  fun getSoundVolumes() = runTest {
    val volumes = mapOf(
      "sound-1" to 1F,
      "sound-2" to 0.8F,
      "sound-3" to 0.6F,
      "sound-4" to 0.4F,
      "sound-5" to 0.2F,
      "sound-6" to 0F,
    )

    volumes.forEach { (soundId, volume) ->
      service.onSoundVolumeChange(soundId, volume)
    }

    assertEquals(volumes, controller.getSoundVolumes().firstOrNull())
  }

  @Test
  fun getCurrentPreset() = runTest {
    val soundStates1 = sortedMapOf("sound-1" to 1F, "sound-2" to 0.8F)
    val soundStates2 = sortedMapOf("sound-3" to 0.6F, "sound-4" to 0.4F)
    val soundStates3 = sortedMapOf("sound-1" to 0.2F, "sound-2" to 0F)
    mapOf(
      soundStates1 to null,
      soundStates2 to Preset("test-preset-1", soundStates2),
      soundStates3 to Preset("test-preset-2", soundStates3),
    ).forEach { (soundStates, preset) ->
      every { anyConstructed<SoundPlayerManager>().getCurrentPreset() } returns soundStates
      every { presetRepositoryMock.getBySoundStatesFlow(soundStates) } returns flowOf(preset)
      service.onCurrentPresetChange()
      assertEquals(preset, controller.getCurrentPreset().firstOrNull())
    }
  }
}

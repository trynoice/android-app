package com.github.ashutoshgngwr.noice.cast

import com.github.ashutoshgngwr.noice.cast.models.GlobalUiUpdatedEvent
import com.github.ashutoshgngwr.noice.cast.models.PresetNameUpdatedEvent
import com.github.ashutoshgngwr.noice.cast.models.SoundUiUpdatedEvent
import com.github.ashutoshgngwr.noice.engine.SoundPlayer
import com.github.ashutoshgngwr.noice.engine.SoundPlayerManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultCastReceiverUiManagerTest {

  private lateinit var channelMock: CastMessagingChannel
  private lateinit var manager: DefaultCastReceiverUiManager

  @Before
  fun setUp() {
    channelMock = mockk(relaxed = true)
    manager = DefaultCastReceiverUiManager(channelMock)
  }

  @Test
  fun setSoundPlayerManagerState() {
    data class TestCase(
      val managerState: SoundPlayerManager.State,
      val managerVolume: Float,
      val expectedManagerState: String,
      val expectedVolume: Float,
    )

    listOf(
      TestCase(
        managerState = SoundPlayerManager.State.PLAYING,
        expectedVolume = 0.6F,
        expectedManagerState = "playing",
        managerVolume = 0.6F,
      ),
      TestCase(
        managerState = SoundPlayerManager.State.PAUSING,
        expectedVolume = 0.3F,
        expectedManagerState = "pausing",
        managerVolume = 0.3F,
      ),
      TestCase(
        managerState = SoundPlayerManager.State.PAUSED,
        expectedVolume = 1F,
        expectedManagerState = "paused",
        managerVolume = 1F,
      ),
      TestCase(
        managerState = SoundPlayerManager.State.STOPPING,
        expectedVolume = 0.3F,
        expectedManagerState = "stopping",
        managerVolume = 0.3F,
      ),
      TestCase(
        managerState = SoundPlayerManager.State.STOPPED,
        expectedVolume = 0.1F,
        expectedManagerState = "stopped",
        managerVolume = 0.1F,
      ),
    ).forEach { testCase ->
      manager.setSoundPlayerManagerState(testCase.managerState, testCase.managerVolume)
      verify(exactly = 1) {
        channelMock.send(withArg<GlobalUiUpdatedEvent> { event ->
          assertEquals(testCase.expectedManagerState, event.state)
          assertEquals(testCase.expectedVolume, event.volume)
        })
      }
    }
  }

  @Test
  fun setSoundPlayerState() {
    data class TestCase(
      val soundId: String,
      val soundState: SoundPlayer.State,
      val soundVolume: Float,
      val expectedSoundState: String,
      val expectedVolume: Float,
    )

    listOf(
      TestCase(
        soundId = "test-sound-1",
        soundState = SoundPlayer.State.PLAYING,
        soundVolume = 1F,
        expectedSoundState = "playing",
        expectedVolume = 1F,
      ),
      TestCase(
        soundId = "test-sound-2",
        soundState = SoundPlayer.State.BUFFERING,
        soundVolume = 0.8F,
        expectedSoundState = "buffering",
        expectedVolume = 0.8F,
      ),
      TestCase(
        soundId = "test-sound-3",
        soundState = SoundPlayer.State.PAUSING,
        soundVolume = 0.9F,
        expectedSoundState = "playing",
        expectedVolume = 0.9F,
      ),
      TestCase(
        soundId = "test-sound-4",
        soundState = SoundPlayer.State.PAUSED,
        soundVolume = 0.7F,
        expectedSoundState = "playing",
        expectedVolume = 0.7F,
      ),
      TestCase(
        soundId = "test-sound-5",
        soundState = SoundPlayer.State.STOPPING,
        soundVolume = 0.5F,
        expectedSoundState = "playing",
        expectedVolume = 0.5F,
      ),
      TestCase(
        soundId = "test-sound-6",
        soundState = SoundPlayer.State.STOPPED,
        soundVolume = 0.6F,
        expectedSoundState = "stopped",
        expectedVolume = 0.6F,
      ),
    ).forEach { testCase ->
      manager.setSoundPlayerState(testCase.soundId, testCase.soundState, testCase.soundVolume)
      verify(exactly = 1) {
        channelMock.send(withArg<SoundUiUpdatedEvent> { event ->
          assertEquals(testCase.soundId, event.soundId)
          assertEquals(testCase.expectedSoundState, event.state)
          assertEquals(testCase.expectedVolume, event.volume)
        })
      }
    }
  }

  @Test
  fun setPresetName() {
    mapOf(
      "preset-1" to "preset-1",
      null to null,
    ).forEach { (currentPresetName, expectedPresetName) ->
      manager.setPresetName(currentPresetName)
      verify(exactly = 1) {
        channelMock.send(withArg<PresetNameUpdatedEvent> { event ->
          assertEquals(expectedPresetName, event.name)
        })
      }
    }
  }
}

package com.github.ashutoshgngwr.noice.engine

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class SoundPlayerManagerTest {

  private lateinit var fakeSoundPlayerFactory: FakeSoundPlayer.Factory
  private lateinit var focusManagerMock: AudioFocusManager
  private lateinit var listenerMock: SoundPlayerManager.Listener
  private lateinit var manager: SoundPlayerManager

  @Before
  fun setUp() {
    fakeSoundPlayerFactory = FakeSoundPlayer.Factory()
    focusManagerMock = mockk(relaxed = true) {
      every { hasFocus } returns true
    }

    listenerMock = mockk(relaxed = true)
    manager = SoundPlayerManager(fakeSoundPlayerFactory, focusManagerMock, listenerMock)
  }

  @Test
  fun setFadeInDuration_setFadeOutDuration_setPremiumSegmentsEnabled_setAudioBitrate_setAudioAttributes() {
    manager.playSound("test-sound-id-1")

    val fadeInDuration = 22.seconds
    val fadeOutDuration = 29.seconds
    val premiumSegmentsEnabled = true
    val audioBitrate = "320k"
    val audioAttrs = AudioAttributes.Builder()
      .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
      .setUsage(C.USAGE_ALARM)
      .build()

    manager.setFadeInDuration(fadeInDuration)
    manager.setFadeOutDuration(fadeOutDuration)
    manager.setPremiumSegmentsEnabled(premiumSegmentsEnabled)
    manager.setAudioBitrate(audioBitrate)
    manager.setAudioAttributes(audioAttrs)

    manager.playSound("test-sound-id-2")

    fakeSoundPlayerFactory.builtInstances.values.forEach { soundPlayer ->
      assertEquals(fadeInDuration, soundPlayer.fadeInDuration)
      assertEquals(fadeOutDuration, soundPlayer.fadeOutDuration)
      assertEquals(premiumSegmentsEnabled, soundPlayer.isPremiumSegmentsEnabled)
      assertEquals(audioBitrate, soundPlayer.audioBitrate)
      assertEquals(audioAttrs, soundPlayer.audioAttributes)
    }
  }

  @Test
  fun setSoundPlayerFactory() {
    data class SoundSpec(
      val soundId: String,
      val currentState: SoundPlayer.State,
      val expectedStateIn: Set<SoundPlayer.State>,
    )

    val specs = listOf(
      SoundSpec(
        soundId = "test-sound-id-1",
        currentState = SoundPlayer.State.BUFFERING,
        expectedStateIn = setOf(SoundPlayer.State.BUFFERING, SoundPlayer.State.PLAYING),
      ),
      SoundSpec(
        soundId = "test-sound-id-2",
        currentState = SoundPlayer.State.PLAYING,
        expectedStateIn = setOf(SoundPlayer.State.BUFFERING, SoundPlayer.State.PLAYING),
      ),
      SoundSpec(
        soundId = "test-sound-id-3",
        currentState = SoundPlayer.State.PAUSING,
        expectedStateIn = setOf(SoundPlayer.State.PAUSING, SoundPlayer.State.PAUSED),
      ),
      SoundSpec(
        soundId = "test-sound-id-4",
        currentState = SoundPlayer.State.PAUSED,
        expectedStateIn = setOf(SoundPlayer.State.PAUSING, SoundPlayer.State.PAUSED),
      ),
      SoundSpec(
        soundId = "test-sound-id-5",
        currentState = SoundPlayer.State.STOPPING,
        expectedStateIn = setOf(SoundPlayer.State.STOPPING, SoundPlayer.State.STOPPED),
      ),
      SoundSpec(
        soundId = "test-sound-id-6",
        currentState = SoundPlayer.State.STOPPED,
        expectedStateIn = setOf(SoundPlayer.State.STOPPING, SoundPlayer.State.STOPPED),
      ),
    )

    specs.forEach { spec ->
      manager.playSound(spec.soundId)
      fakeSoundPlayerFactory.builtInstances
        .getValue(spec.soundId)
        .setStateTo(spec.currentState)
    }

    manager.setSoundPlayerFactory(fakeSoundPlayerFactory)
    specs.forEach { spec ->
      fakeSoundPlayerFactory.builtInstances
        .getValue(spec.soundId)
        .state
        .also { assertTrue(it in spec.expectedStateIn) }
    }

    val newFakeSoundPlayerFactory = FakeSoundPlayer.Factory()
    manager.setSoundPlayerFactory(newFakeSoundPlayerFactory)
    fakeSoundPlayerFactory.builtInstances.values.forEach { soundPlayer ->
      assertEquals(SoundPlayer.State.STOPPED, soundPlayer.state)
    }

    specs.forEach { spec ->
      val newState = newFakeSoundPlayerFactory.builtInstances[spec.soundId]
        ?.state
        ?: SoundPlayer.State.STOPPED

      assertTrue("for ${spec.soundId}, got $newState", newState in spec.expectedStateIn)
    }
  }

  @Test
  fun setVolume_setSoundVolume() {
    manager.setSoundVolume("test-sound-id-1", 0.6F)
    verify(exactly = 1) { listenerMock.onSoundVolumeChange("test-sound-id-1", 0.6F) }

    manager.playSound("test-sound-id-1")
    val soundPlayer1 = fakeSoundPlayerFactory.builtInstances.getValue("test-sound-id-1")

    manager.setVolume(0.8F)
    assertEquals(0.6F * 0.8F, soundPlayer1.volume)
    verify(exactly = 1) { listenerMock.onSoundPlayerManagerVolumeChange(0.8F) }

    manager.playSound("test-sound-id-2")
    val soundPlayer2 = fakeSoundPlayerFactory.builtInstances.getValue("test-sound-id-2")
    assertEquals(0.8F, soundPlayer2.volume)
    verify(exactly = 0) { listenerMock.onSoundVolumeChange("test-sound-id-2", any()) }

    manager.setSoundVolume("test-sound-id-2", 0.5F)
    assertEquals(0.8F * 0.5F, soundPlayer2.volume)
    verify(exactly = 1) { listenerMock.onSoundVolumeChange("test-sound-id-2", 0.5F) }

    clearMocks(listenerMock)
    manager.setVolume(0.4F)
    assertEquals(0.4F * 0.6F, soundPlayer1.volume)
    assertEquals(0.4F * 0.5F, soundPlayer2.volume)
    verify(exactly = 1) { listenerMock.onSoundPlayerManagerVolumeChange(0.4F) }
    verify(exactly = 0) { listenerMock.onSoundVolumeChange(any(), any()) }

    assertThrows(IllegalArgumentException::class.java) { manager.setVolume(1.1F) }
    assertThrows(IllegalArgumentException::class.java) {
      manager.setSoundVolume("test-sound-id-1", 1.1F)
    }

    assertThrows(IllegalArgumentException::class.java) { manager.setVolume(-1F) }
    assertThrows(IllegalArgumentException::class.java) {
      manager.setSoundVolume("test-sound-id-2", -1F)
    }
  }

  @Test
  fun playSound() {
    every { focusManagerMock.hasFocus } returns false
    assertEquals(SoundPlayerManager.State.STOPPED, manager.state)
    manager.playSound("test-sound-id-1")
    val soundPlayer1 = fakeSoundPlayerFactory.builtInstances.getValue("test-sound-id-1")
    verify(exactly = 1) {
      listenerMock.onSoundPlayerManagerStateChange(SoundPlayerManager.State.PAUSED)
      listenerMock.onSoundStateChange("test-sound-id-1", SoundPlayer.State.PAUSED)
    }

    every { focusManagerMock.hasFocus } returns true
    getAudioFocusListener().onAudioFocusGained()
    verify(exactly = 1) {
      listenerMock.onSoundPlayerManagerStateChange(SoundPlayerManager.State.PLAYING)
      listenerMock.onSoundStateChange("test-sound-id-1", SoundPlayer.State.BUFFERING)
    }

    soundPlayer1.setStateTo(SoundPlayer.State.PLAYING)
    assertEquals(SoundPlayerManager.State.PLAYING, manager.state)
    verify(exactly = 1) {
      listenerMock.onSoundStateChange("test-sound-id-1", SoundPlayer.State.PLAYING)
    }

    manager.playSound("test-sound-id-2")
    val soundPlayer2 = fakeSoundPlayerFactory.builtInstances.getValue("test-sound-id-2")
    assertEquals(SoundPlayerManager.State.PLAYING, manager.state)
    verify(exactly = 1) {
      listenerMock.onSoundStateChange("test-sound-id-2", SoundPlayer.State.BUFFERING)
    }

    soundPlayer2.setStateTo(SoundPlayer.State.PLAYING)
    assertEquals(SoundPlayerManager.State.PLAYING, manager.state)
    verify(exactly = 1) {
      listenerMock.onSoundStateChange("test-sound-id-2", SoundPlayer.State.PLAYING)
    }
  }

  @Test
  fun stopSound() {
    manager.playSound("test-sound-id-1")
    manager.playSound("test-sound-id-2")

    val soundPlayer1 = fakeSoundPlayerFactory.builtInstances.getValue("test-sound-id-1")
    val soundPlayer2 = fakeSoundPlayerFactory.builtInstances.getValue("test-sound-id-2")
    soundPlayer1.setStateTo(SoundPlayer.State.PLAYING)
    soundPlayer2.setStateTo(SoundPlayer.State.PLAYING)

    manager.stopSound("test-sound-id-1")
    assertEquals(SoundPlayer.State.STOPPING, soundPlayer1.state)
    assertEquals(SoundPlayer.State.PLAYING, soundPlayer2.state)
    verify(exactly = 1) {
      listenerMock.onSoundStateChange("test-sound-id-1", SoundPlayer.State.STOPPING)
    }

    manager.stopSound("test-sound-id-2")
    assertEquals(SoundPlayer.State.STOPPING, soundPlayer2.state)
    verify(exactly = 1) {
      listenerMock.onSoundStateChange("test-sound-id-2", SoundPlayer.State.STOPPING)
    }

    // shouldn't do anything if a sound isn't playing
    manager.stopSound("test-sound-id-3")
    verify(exactly = 0) { listenerMock.onSoundStateChange("test-sound-id-3", any()) }
  }

  @Test
  fun stop_resume() {
    manager.playSound("test-sound-id-1")
    manager.playSound("test-sound-id-2")
    fakeSoundPlayerFactory.builtInstances.values.forEach { it.setStateTo(SoundPlayer.State.PLAYING) }

    manager.stop(true)
    verify(exactly = 1) {
      listenerMock.onSoundPlayerManagerStateChange(SoundPlayerManager.State.STOPPED)
      listenerMock.onSoundStateChange("test-sound-id-1", SoundPlayer.State.STOPPED)
      listenerMock.onSoundStateChange("test-sound-id-2", SoundPlayer.State.STOPPED)
    }

    clearMocks(listenerMock)
    manager.playSound("test-sound-id-1")
    manager.playSound("test-sound-id-2")
    fakeSoundPlayerFactory.builtInstances.values.forEach { it.setStateTo(SoundPlayer.State.PLAYING) }

    manager.stop(false)
    verify(exactly = 1) {
      listenerMock.onSoundPlayerManagerStateChange(SoundPlayerManager.State.STOPPING)
      listenerMock.onSoundStateChange("test-sound-id-1", SoundPlayer.State.STOPPING)
      listenerMock.onSoundStateChange("test-sound-id-2", SoundPlayer.State.STOPPING)
    }

    clearMocks(listenerMock)
    manager.resume()
    verify(exactly = 1) {
      listenerMock.onSoundPlayerManagerStateChange(SoundPlayerManager.State.PLAYING)
      listenerMock.onSoundStateChange("test-sound-id-1", SoundPlayer.State.BUFFERING)
      listenerMock.onSoundStateChange("test-sound-id-2", SoundPlayer.State.BUFFERING)
    }

    manager.stop(false)
    verify(exactly = 1) {
      listenerMock.onSoundPlayerManagerStateChange(SoundPlayerManager.State.STOPPING)
      listenerMock.onSoundStateChange("test-sound-id-1", SoundPlayer.State.STOPPING)
      listenerMock.onSoundStateChange("test-sound-id-2", SoundPlayer.State.STOPPING)
    }

    fakeSoundPlayerFactory.builtInstances.values.forEach { it.setStateTo(SoundPlayer.State.STOPPED) }
    verify(exactly = 1) {
      listenerMock.onSoundPlayerManagerStateChange(SoundPlayerManager.State.STOPPED)
      listenerMock.onSoundStateChange("test-sound-id-1", SoundPlayer.State.STOPPED)
      listenerMock.onSoundStateChange("test-sound-id-2", SoundPlayer.State.STOPPED)
    }
  }

  @Test
  fun pause_resume() {
    every { focusManagerMock.hasFocus } returns false
    manager.playSound("test-sound-id-1")
    manager.playSound("test-sound-id-2")

    manager.pause(true)
    verify(exactly = 1) {
      listenerMock.onSoundPlayerManagerStateChange(SoundPlayerManager.State.PAUSED)
      listenerMock.onSoundStateChange("test-sound-id-1", SoundPlayer.State.PAUSED)
      listenerMock.onSoundStateChange("test-sound-id-2", SoundPlayer.State.PAUSED)
    }

    // should not automatically resume on focus gain after pause
    every { focusManagerMock.hasFocus } returns true
    getAudioFocusListener().onAudioFocusGained()
    assertEquals(SoundPlayerManager.State.PAUSED, manager.state)
    fakeSoundPlayerFactory.builtInstances.values.forEach { soundPlayer ->
      assertEquals(SoundPlayer.State.PAUSED, soundPlayer.state)
    }

    clearMocks(listenerMock)
    manager.resume()
    verify(exactly = 1) {
      listenerMock.onSoundPlayerManagerStateChange(SoundPlayerManager.State.PLAYING)
      listenerMock.onSoundStateChange("test-sound-id-1", SoundPlayer.State.BUFFERING)
      listenerMock.onSoundStateChange("test-sound-id-2", SoundPlayer.State.BUFFERING)
    }

    fakeSoundPlayerFactory.builtInstances.values.forEach { it.setStateTo(SoundPlayer.State.PLAYING) }
    manager.pause(false)
    verify(exactly = 1) {
      listenerMock.onSoundPlayerManagerStateChange(SoundPlayerManager.State.PAUSING)
      listenerMock.onSoundStateChange("test-sound-id-1", SoundPlayer.State.PAUSING)
      listenerMock.onSoundStateChange("test-sound-id-2", SoundPlayer.State.PAUSING)
    }

    fakeSoundPlayerFactory.builtInstances.values.forEach { it.setStateTo(SoundPlayer.State.PAUSED) }
    verify(exactly = 1) {
      listenerMock.onSoundPlayerManagerStateChange(SoundPlayerManager.State.PAUSED)
      listenerMock.onSoundStateChange("test-sound-id-1", SoundPlayer.State.PAUSED)
      listenerMock.onSoundStateChange("test-sound-id-2", SoundPlayer.State.PAUSED)
    }

    clearMocks(listenerMock)
    every { focusManagerMock.hasFocus } returns false
    manager.resume()
    assertEquals(SoundPlayerManager.State.PAUSED, manager.state)
    fakeSoundPlayerFactory.builtInstances.values.forEach { soundPlayer ->
      assertEquals(SoundPlayer.State.PAUSED, soundPlayer.state)
    }

    every { focusManagerMock.hasFocus } returns true
    getAudioFocusListener().onAudioFocusGained()
    verify(exactly = 1) {
      listenerMock.onSoundPlayerManagerStateChange(SoundPlayerManager.State.PLAYING)
      listenerMock.onSoundStateChange("test-sound-id-1", SoundPlayer.State.BUFFERING)
      listenerMock.onSoundStateChange("test-sound-id-2", SoundPlayer.State.BUFFERING)
    }
  }

  @Test
  fun stopSound_pause() {
    manager.playSound("test-sound-id-1")
    manager.playSound("test-sound-id-2")
    manager.stopSound("test-sound-id-2")

    val soundPlayer1 = fakeSoundPlayerFactory.builtInstances.getValue("test-sound-id-1")
    val soundPlayer2 = fakeSoundPlayerFactory.builtInstances.getValue("test-sound-id-2")
    assertEquals(SoundPlayer.State.BUFFERING, soundPlayer1.state)
    assertEquals(SoundPlayer.State.STOPPING, soundPlayer2.state)

    manager.pause(false)
    assertEquals(SoundPlayer.State.PAUSING, soundPlayer1.state)
    assertEquals(SoundPlayer.State.STOPPING, soundPlayer2.state)
  }

  @Test
  fun stop_pause() {
    manager.playSound("test-sound-id-1")
    manager.playSound("test-sound-id-2")
    manager.stop(false)

    val soundPlayer1 = fakeSoundPlayerFactory.builtInstances.getValue("test-sound-id-1")
    val soundPlayer2 = fakeSoundPlayerFactory.builtInstances.getValue("test-sound-id-2")
    assertEquals(SoundPlayerManager.State.STOPPING, manager.state)
    assertEquals(SoundPlayer.State.STOPPING, soundPlayer1.state)
    assertEquals(SoundPlayer.State.STOPPING, soundPlayer2.state)

    manager.pause(false)
    assertEquals(SoundPlayerManager.State.PAUSING, manager.state)
    assertEquals(SoundPlayer.State.PAUSING, soundPlayer1.state)
    assertEquals(SoundPlayer.State.PAUSING, soundPlayer2.state)
  }

  @Test
  fun audioFocusLoss() {
    manager.playSound("test-sound-id-1")
    manager.playSound("test-sound-id-2")
    fakeSoundPlayerFactory.builtInstances.values.forEach { it.setStateTo(SoundPlayer.State.PLAYING) }
    assertEquals(SoundPlayerManager.State.PLAYING, manager.state)

    every { focusManagerMock.hasFocus } returns false
    getAudioFocusListener().onAudioFocusLost(true)
    assertEquals(SoundPlayerManager.State.PAUSED, manager.state)

    // should resume on regaining focus.
    every { focusManagerMock.hasFocus } returns true
    getAudioFocusListener().onAudioFocusGained()
    assertEquals(SoundPlayerManager.State.PLAYING, manager.state)

    every { focusManagerMock.hasFocus } returns false
    getAudioFocusListener().onAudioFocusLost(false)
    assertEquals(SoundPlayerManager.State.PAUSED, manager.state)
  }

  @Test
  fun playPreset() {
    manager.playSound("test-sound-id-1")
    manager.setSoundVolume("test-sound-id-1", 0.8F)
    manager.playSound("test-sound-id-2")
    manager.setSoundVolume("test-sound-id-2", 0.9F)
    manager.playSound("test-sound-id-3")
    manager.setSoundVolume("test-sound-id-2", 1F)
    fakeSoundPlayerFactory.builtInstances.values.forEach { soundPlayer ->
      assertEquals(SoundPlayer.State.BUFFERING, soundPlayer.state)
    }

    val preset = sortedMapOf(
      "test-sound-id-2" to 0.6F,
      "test-sound-id-3" to 0.7F,
      "test-sound-id-4" to 0.8F,
    )

    manager.playPreset(preset)

    val soundPlayer1 = fakeSoundPlayerFactory.builtInstances.getValue("test-sound-id-1")
    assertEquals(SoundPlayer.State.STOPPING, soundPlayer1.state)

    preset.forEach { (soundId, expectedVolume) ->
      val soundPlayer = fakeSoundPlayerFactory.builtInstances[soundId]
      assertEquals(SoundPlayer.State.BUFFERING, soundPlayer?.state)
      assertEquals(expectedVolume, soundPlayer?.volume)
    }
  }

  @Test
  fun getCurrentPreset_duringNormalPlayback() {
    data class SoundSpec(
      val soundId: String,
      val currentState: SoundPlayer.State,
      val currentVolume: Float,
      val expectedInPreset: Boolean,
    )

    val specs = listOf(
      SoundSpec(
        soundId = "test-sound-id-1",
        currentState = SoundPlayer.State.BUFFERING,
        currentVolume = 0.5F,
        expectedInPreset = true,
      ),
      SoundSpec(
        soundId = "test-sound-id-2",
        currentState = SoundPlayer.State.PLAYING,
        currentVolume = 0.6F,
        expectedInPreset = true,
      ),
      SoundSpec(
        soundId = "test-sound-id-3",
        currentState = SoundPlayer.State.PAUSING,
        currentVolume = 0.7F,
        expectedInPreset = true,
      ),
      SoundSpec(
        soundId = "test-sound-id-4",
        currentState = SoundPlayer.State.PAUSED,
        currentVolume = 0.8F,
        expectedInPreset = true,
      ),
      SoundSpec(
        soundId = "test-sound-id-5",
        currentState = SoundPlayer.State.STOPPING,
        currentVolume = 0.9F,
        expectedInPreset = false,
      ),
      SoundSpec(
        soundId = "test-sound-id-6",
        currentState = SoundPlayer.State.STOPPED,
        currentVolume = 1F,
        expectedInPreset = false,
      ),
    )

    specs.forEach { spec ->
      manager.playSound(spec.soundId)
      manager.setSoundVolume(spec.soundId, spec.currentVolume)
      fakeSoundPlayerFactory.builtInstances
        .getValue(spec.soundId)
        .setStateTo(spec.currentState)
    }

    val currentPreset = manager.getCurrentPreset()
    specs.forEach { spec ->
      if (spec.expectedInPreset) {
        assertEquals(spec.currentVolume, currentPreset[spec.soundId])
      } else {
        assertNull(currentPreset[spec.soundId])
      }
    }
  }

  @Test
  fun getCurrentPreset_duringStoppingPhase() {
    manager.playSound("test-sound-id-1")
    manager.playSound("test-sound-id-2")
    manager.stop(false)
    assertEquals(SoundPlayerManager.State.STOPPING, manager.state)
    assertTrue("test-sound-id-1" in manager.getCurrentPreset())
    assertTrue("test-sound-id-2" in manager.getCurrentPreset())
  }

  private fun getAudioFocusListener(): AudioFocusManager.Listener {
    val focusManagerListenerSlot = slot<AudioFocusManager.Listener>()
    verify { focusManagerMock.setListener(capture(focusManagerListenerSlot)) }
    assertNotNull(focusManagerListenerSlot.captured)
    return focusManagerListenerSlot.captured
  }
}

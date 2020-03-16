package com.github.ashutoshgngwr.noice.sound

import android.media.AudioManager
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class PlaybackManagerTest {

  @Mock
  private lateinit var eventBus: EventBus

  @InjectMocks
  private lateinit var playbackManager: PlaybackManager

  @Before
  @UiThreadTest
  fun setup() {
    playbackManager = PlaybackManager(InstrumentationRegistry.getInstrumentation().targetContext)
    MockitoAnnotations.initMocks(this)
  }

  @After
  fun teardown() {
    if (this::playbackManager.isInitialized) {
      playbackManager.stopPlayback(PlaybackControlEvents.StopPlaybackEvent())
    }
  }

  @Test
  @UiThreadTest
  fun testPlaybackControls() {
    val playbackStateCaptor = ArgumentCaptor.forClass(HashMap::class.java)

    // lets try to play something, playing a sound first time will cause PLAYING event to be delivered
    // twice. Once when it is requesting focus; and then once when it gets it and starts the playback.
    playbackManager.startPlayback(PlaybackControlEvents.StartPlaybackEvent("birds"))
    verify(eventBus, atLeastOnce()).post(PlaybackManager.UpdateEvent(PlaybackManager.State.PLAYING))
    verify(eventBus, atLeastOnce()).postSticky(playbackStateCaptor.capture())
    // check if sound is actually playing
    var playback = playbackStateCaptor.value["birds"] as Playback
    assertNotNull(playback)
    assertTrue(playback.isPlaying)
    // reset invocations
    reset(eventBus)

    playbackManager.startPlayback(PlaybackControlEvents.StartPlaybackEvent("rolling_thunder"))
    verify(eventBus, atLeastOnce()).post(PlaybackManager.UpdateEvent(PlaybackManager.State.PLAYING))
    verify(eventBus, atLeastOnce()).postSticky(playbackStateCaptor.capture())
    // check if sound is actually playing
    playback = playbackStateCaptor.value["rolling_thunder"] as Playback
    assertNotNull(playback)
    assertTrue(playback.isPlaying)
    // reset invocations
    reset(eventBus)

    // try updating playback settings. this doesn't publish any update events so verifying these
    // calls in the pause playback section
    playback.setVolume(Playback.MAX_VOLUME)
    playback.timePeriod = Playback.MAX_TIME_PERIOD
    playbackManager.updatePlayback(PlaybackControlEvents.UpdatePlaybackEvent(playback))

    // playback manager should indicate playing paused after pausing
    playbackManager.pausePlayback(PlaybackControlEvents.PausePlaybackEvent())
    verify(eventBus, atLeastOnce()).post(PlaybackManager.UpdateEvent(PlaybackManager.State.PAUSED))
    verify(eventBus, atLeastOnce()).postSticky(playbackStateCaptor.capture())
    // check if sound is actually paused
    playback = playbackStateCaptor.value["birds"] as Playback
    assertFalse(playback.isPlaying)
    playback = playbackStateCaptor.value["rolling_thunder"] as Playback
    assertFalse(playback.isPlaying)
    assertEquals(Playback.MAX_VOLUME, playback.volume)
    assertEquals(Playback.MAX_TIME_PERIOD, playback.timePeriod)
    // reset invocations
    reset(eventBus)

    // try resuming
    playbackManager.startPlayback(PlaybackControlEvents.StartPlaybackEvent(null))
    verify(eventBus, atLeastOnce()).post(PlaybackManager.UpdateEvent(PlaybackManager.State.PLAYING))
    verify(eventBus, atLeastOnce()).postSticky(playbackStateCaptor.capture())
    // reset invocations
    reset(eventBus)

    // stop you fools. STOP!!!!
    playbackManager.stopPlayback(PlaybackControlEvents.StopPlaybackEvent("birds"))
    playbackManager.stopPlayback(PlaybackControlEvents.StopPlaybackEvent("rolling_thunder"))
    verify(eventBus, atLeastOnce()).post(PlaybackManager.UpdateEvent(PlaybackManager.State.STOPPED))
    verify(eventBus, atLeastOnce()).postSticky(playbackStateCaptor.capture())
    assertNull(playbackStateCaptor.value["birds"])
    assertNull(playbackStateCaptor.value["rolling_thunder"])
  }

  @Test
  @UiThreadTest
  fun testPlaybackStateOnFocusLoss() {
    playbackManager.startPlayback(PlaybackControlEvents.StartPlaybackEvent("birds"))
    verify(eventBus, atLeastOnce()).post(PlaybackManager.UpdateEvent(PlaybackManager.State.PLAYING))

    // test transient loss
    playbackManager.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
    verify(eventBus, atLeastOnce()).post(PlaybackManager.UpdateEvent(PlaybackManager.State.PAUSED))

    // test regain focus
    playbackManager.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
    verify(eventBus, atLeastOnce()).post(PlaybackManager.UpdateEvent(PlaybackManager.State.PLAYING))

    // test permanent loss of focus
    playbackManager.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
    verify(eventBus, atLeastOnce()).post(PlaybackManager.UpdateEvent(PlaybackManager.State.PAUSED))
  }
}

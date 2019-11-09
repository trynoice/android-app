package com.github.ashutoshgngwr.noice

import android.media.AudioAttributes
import com.github.ashutoshgngwr.noice.fragment.PresetFragment
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment.Sound.Companion.LIBRARY
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSoundPool

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [21, 28])
class SoundManagerTest {

  private lateinit var mSoundPoolShadow: ShadowSoundPool
  private lateinit var mSoundManager: SoundManager

  @Before
  fun setup() {
    mSoundManager = SoundManager(RuntimeEnvironment.systemContext, Shadow.newInstanceOf(AudioAttributes::class.java))
    mSoundPoolShadow = shadowOf(mSoundManager.mSoundPool)
  }

  @Test
  fun `should play loopable sound`() {
    mSoundManager.play(LIBRARY[0].key)
    assert(mSoundManager.isPlaying && !mSoundManager.isPaused())
    assert(mSoundPoolShadow.wasResourcePlayed(LIBRARY[0].resId))
  }

  @Test
  fun `should play non-loopable sound`() {
    mSoundManager.play(LIBRARY[3].key)
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(mSoundManager.isPlaying && !mSoundManager.isPaused())
    assert(mSoundPoolShadow.wasResourcePlayed(LIBRARY[3].resId) && mSoundManager.isPlaying(LIBRARY[3].key))

    mSoundPoolShadow.clearPlayed()
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(
      mSoundPoolShadow.wasResourcePlayed(LIBRARY[3].resId)
        && mSoundManager.isPlaying(LIBRARY[3].key)
    )
  }

  @Test
  fun `should add newly played sounds to pause state if playback is paused`() {
    mSoundManager.play(LIBRARY[0].key)
    assert(mSoundManager.isPlaying && !mSoundManager.isPaused())

    mSoundManager.pausePlayback()
    mSoundManager.play(LIBRARY[1].key)
    mSoundManager.play(LIBRARY[0].key) // playing twice shouldn't affect pause state
    assert(!mSoundManager.isPlaying && mSoundManager.isPaused())

    mSoundPoolShadow.clearPlayed()
    mSoundManager.resumePlayback()
    assert(mSoundManager.isPlaying && !mSoundManager.isPaused())
    assert(mSoundPoolShadow.wasResourcePlayed(LIBRARY[0].resId) && mSoundPoolShadow.wasResourcePlayed(LIBRARY[1].resId))
  }

  @Test
  fun `should stop loopable sounds`() {
    mSoundManager.play(LIBRARY[0].key)
    mSoundManager.play(LIBRARY[1].key)
    assert(mSoundPoolShadow.wasResourcePlayed(LIBRARY[0].resId) && mSoundPoolShadow.wasResourcePlayed(LIBRARY[1].resId))

    mSoundPoolShadow.clearPlayed()

    mSoundManager.stop(LIBRARY[0].key)
    mSoundManager.stop(LIBRARY[1].key)
    assert(
      !mSoundPoolShadow.wasResourcePlayed(LIBRARY[0].resId)
        && !mSoundPoolShadow.wasResourcePlayed(LIBRARY[1].resId)
    )
  }

  @Test
  fun `should stop non-loopable sound`() {
    // stopping a stopped sound should have no effect
    mSoundManager.stop(LIBRARY[3].key)
    assert(!mSoundManager.isPlaying(LIBRARY[3].key))

    mSoundManager.play(LIBRARY[3].key)
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(mSoundPoolShadow.wasResourcePlayed(LIBRARY[3].resId))

    mSoundPoolShadow.clearPlayed()

    mSoundManager.stop(LIBRARY[3].key)
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(!mSoundPoolShadow.wasResourcePlayed(LIBRARY[3].resId))
  }

  @Test
  fun `should stop playback`() {
    // stopPlayback without playback state should have no effect
    mSoundManager.stopPlayback()
    assert(!mSoundManager.isPlaying && !mSoundManager.isPaused())

    mSoundManager.play(LIBRARY[3].key)
    mSoundManager.play(LIBRARY[0].key)
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(
      mSoundPoolShadow.wasResourcePlayed(LIBRARY[3].resId)
        && mSoundPoolShadow.wasResourcePlayed(LIBRARY[0].resId)
    )

    mSoundPoolShadow.clearPlayed()
    mSoundManager.stopPlayback()
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(
      !mSoundPoolShadow.wasResourcePlayed(LIBRARY[3].resId)
        && !mSoundPoolShadow.wasResourcePlayed(LIBRARY[0].resId)
    )
  }

  @Test
  fun `should pause playback`() {
    // pause without playback state should have no effect
    mSoundManager.pausePlayback()
    assert(!mSoundManager.isPlaying)

    mSoundManager.play(LIBRARY[3].key)
    mSoundManager.play(LIBRARY[0].key)
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(
      mSoundPoolShadow.wasResourcePlayed(LIBRARY[3].resId)
        && mSoundPoolShadow.wasResourcePlayed(LIBRARY[0].resId)
    )

    mSoundPoolShadow.clearPlayed()
    mSoundManager.pausePlayback()
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(
      !mSoundPoolShadow.wasResourcePlayed(LIBRARY[3].resId)
        && !mSoundPoolShadow.wasResourcePlayed(LIBRARY[0].resId)
        && mSoundManager.isPaused()
    )
  }

  @Test
  fun `should resume playback`() {
    // resume without pause state should not change playback state
    mSoundManager.resumePlayback()
    assert(!mSoundManager.isPlaying)

    mSoundManager.play(LIBRARY[3].key)
    mSoundManager.play(LIBRARY[0].key)
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(
      mSoundPoolShadow.wasResourcePlayed(LIBRARY[3].resId)
        && mSoundPoolShadow.wasResourcePlayed(LIBRARY[0].resId)
    )

    mSoundPoolShadow.clearPlayed()

    mSoundManager.pausePlayback()
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(
      !mSoundPoolShadow.wasResourcePlayed(LIBRARY[3].resId)
        && !mSoundPoolShadow.wasResourcePlayed(LIBRARY[0].resId)
        && mSoundManager.isPaused()
    )

    mSoundManager.resumePlayback()
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(
      mSoundPoolShadow.wasResourcePlayed(LIBRARY[3].resId)
        && mSoundPoolShadow.wasResourcePlayed(LIBRARY[0].resId)
        && !mSoundManager.isPaused()
    )
  }

  @Test
  fun `should notify playback listeners of changes`() {
    var isCalled = false
    mSoundManager.addOnPlaybackStateChangeListener(
      object : SoundManager.OnPlaybackStateChangeListener {
        override fun onPlaybackStateChanged(playbackState: Int) {
          isCalled = true
        }
      }
    )

    mSoundManager.play(LIBRARY[3].key)
    assert(isCalled)
  }

  @Test
  fun `should not notify removed playback listeners of changes`() {
    var isCalled = false
    val playbackListener = object : SoundManager.OnPlaybackStateChangeListener {
      override fun onPlaybackStateChanged(playbackState: Int) {
        isCalled = true
      }
    }

    mSoundManager.addOnPlaybackStateChangeListener(playbackListener)
    mSoundManager.removeOnPlaybackStateChangeListener(playbackListener)
    mSoundManager.play(LIBRARY[3].key)
    assert(!isCalled)
  }

  @Test
  fun `should get playing state of a sound`() {
    assert(!mSoundManager.isPlaying(LIBRARY[3].key))

    mSoundManager.play(LIBRARY[3].key)
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(
      mSoundPoolShadow.wasResourcePlayed(LIBRARY[3].resId)
        && mSoundManager.isPlaying(LIBRARY[3].key)
    )

    mSoundManager.stopPlayback()
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(!mSoundManager.isPlaying(LIBRARY[3].key))
  }

  @Test
  fun `should get and set volume`() {
    assert(mSoundManager.getVolume(LIBRARY[3].key) == SoundLibraryFragment.Sound.DEFAULT_VOLUME)

    mSoundManager.setVolume(LIBRARY[3].key, 15)
    assert(mSoundManager.getVolume(LIBRARY[3].key) == 15)
  }

  @Test
  fun `should get and set time period`() {
    assert(mSoundManager.getTimePeriod(LIBRARY[3].key) == SoundLibraryFragment.Sound.DEFAULT_TIME_PERIOD)

    mSoundManager.setTimePeriod(LIBRARY[3].key, 120)
    assert(mSoundManager.getTimePeriod(LIBRARY[3].key) == 120)
  }

  @Test
  fun `should return null preset if playback is paused or stopped`() {
    assert(!mSoundManager.isPlaying && mSoundManager.getCurrentPreset() == null)
    mSoundManager.play(LIBRARY[0].key)
    mSoundManager.pausePlayback()
    assert(mSoundPoolShadow.wasResourcePlayed(LIBRARY[0].resId) && mSoundManager.getCurrentPreset() == null)
  }

  @Test
  fun `should return current preset if playback is on`() {
    mSoundManager.play(LIBRARY[0].key)
    mSoundManager.play(LIBRARY[3].key)
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(mSoundPoolShadow.wasResourcePlayed(LIBRARY[0].resId) && mSoundPoolShadow.wasResourcePlayed(LIBRARY[3].resId))

    val preset = mSoundManager.getCurrentPreset()!!
    assert(preset.playbackStates.size == 2)
    assert(
      preset.playbackStates.contains(PresetFragment.Preset.PresetPlaybackState(LIBRARY[0].key, 0.2f, 60))
        && preset.playbackStates.contains(PresetFragment.Preset.PresetPlaybackState(LIBRARY[3].key, 0.2f, 60))
    )
  }

  @Test
  fun `should correctly play given preset`() {
    mSoundManager.playPreset(
      PresetFragment.Preset(
        "random", arrayOf(
          PresetFragment.Preset.PresetPlaybackState(LIBRARY[0].key, 0.2f, 60),
          PresetFragment.Preset.PresetPlaybackState(LIBRARY[3].key, 0.2f, 60)
        )
      )
    )

    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(mSoundPoolShadow.wasResourcePlayed(LIBRARY[0].resId) && mSoundPoolShadow.wasResourcePlayed(LIBRARY[3].resId))
  }
}

package com.github.ashutoshgngwr.noice

import android.media.AudioAttributes
import com.github.ashutoshgngwr.noice.fragment.PresetFragment
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSoundPool

@RunWith(RobolectricTestRunner::class)
class SoundManagerTest {

  private lateinit var mSoundPool: ShadowSoundPool
  private lateinit var mSoundManager: SoundManager

  @Before
  fun setup() {
    mSoundManager = SoundManager(RuntimeEnvironment.systemContext, Shadow.newInstanceOf(AudioAttributes::class.java))
    mSoundPool = shadowOf(mSoundManager.mSoundPool)
  }

  @Test
  fun `should play loopable sound`() {
    mSoundManager.play("moving_train")
    assert(mSoundManager.isPlaying && !mSoundManager.isPaused())
    assert(mSoundPool.wasResourcePlayed(R.raw.moving_train))
  }

  @Test
  fun `should play non-loopable sound`() {
    mSoundManager.play("train_horn")
    assert(mSoundManager.isPlaying && !mSoundManager.isPaused())
    assert(
      mSoundPool.wasResourcePlayed(R.raw.train_horn)
        && mSoundManager.isPlaying("train_horn")
    )

    mSoundPool.clearPlayed()

    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(
      mSoundPool.wasResourcePlayed(R.raw.train_horn)
        && mSoundManager.isPlaying("train_horn")
    )
  }

  @Test
  fun `should add newly played sounds to pause state if playback is paused`() {
    mSoundManager.play("moving_train")
    assert(mSoundManager.isPlaying && !mSoundManager.isPaused())

    mSoundManager.pausePlayback()
    mSoundManager.play("water_stream")
    mSoundManager.play("moving_train") // playing twice shouldn't affect pause state
    assert(!mSoundManager.isPlaying && mSoundManager.isPaused())

    mSoundPool.clearPlayed()
    mSoundManager.resumePlayback()
    assert(mSoundManager.isPlaying && !mSoundManager.isPaused())
    assert(mSoundPool.wasResourcePlayed(R.raw.moving_train) && mSoundPool.wasResourcePlayed(R.raw.water_stream))
  }

  @Test
  fun `should stop loopable sounds`() {
    mSoundManager.play("moving_train")
    mSoundManager.play("water_stream")
    assert(mSoundPool.wasResourcePlayed(R.raw.moving_train))

    mSoundPool.clearPlayed()

    mSoundManager.stop("moving_train")
    mSoundManager.stop("water_stream")
    assert(
      !mSoundPool.wasResourcePlayed(R.raw.moving_train)
        && !mSoundPool.wasResourcePlayed(R.raw.water_stream)
    )
  }

  @Test
  fun `should stop non-loopable sound`() {
    // stopping a stopped sound should have no effect
    mSoundManager.stop("train_horn")
    assert(!mSoundManager.isPlaying("train_horn"))

    mSoundManager.play("train_horn")
    assert(mSoundPool.wasResourcePlayed(R.raw.train_horn))

    mSoundPool.clearPlayed()

    mSoundManager.stop("train_horn")
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(!mSoundPool.wasResourcePlayed(R.raw.train_horn))
  }

  @Test
  fun `should stop playback`() {
    // stopPlayback without playback state should have no effect
    mSoundManager.stopPlayback()
    assert(!mSoundManager.isPlaying && !mSoundManager.isPaused())

    mSoundManager.play("train_horn")
    mSoundManager.play("moving_train")
    assert(
      mSoundPool.wasResourcePlayed(R.raw.train_horn)
        && mSoundPool.wasResourcePlayed(R.raw.moving_train)
    )

    mSoundPool.clearPlayed()

    mSoundManager.stopPlayback()
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(
      !mSoundPool.wasResourcePlayed(R.raw.train_horn)
        && !mSoundPool.wasResourcePlayed(R.raw.moving_train)
    )
  }

  @Test
  fun `should pause playback`() {
    // pause without playback state should have no effect
    mSoundManager.pausePlayback()
    assert(!mSoundManager.isPlaying)

    mSoundManager.play("train_horn")
    mSoundManager.play("moving_train")
    assert(
      mSoundPool.wasResourcePlayed(R.raw.train_horn)
        && mSoundPool.wasResourcePlayed(R.raw.moving_train)
    )

    mSoundPool.clearPlayed()

    mSoundManager.pausePlayback()
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(
      !mSoundPool.wasResourcePlayed(R.raw.train_horn)
        && !mSoundPool.wasResourcePlayed(R.raw.moving_train)
        && mSoundManager.isPaused()
    )
  }

  @Test
  fun `should resume playback`() {
    // resume without pause state should not change playback state
    mSoundManager.resumePlayback()
    assert(!mSoundManager.isPlaying)

    mSoundManager.play("train_horn")
    mSoundManager.play("moving_train")
    assert(
      mSoundPool.wasResourcePlayed(R.raw.train_horn)
        && mSoundPool.wasResourcePlayed(R.raw.moving_train)
    )

    mSoundPool.clearPlayed()

    mSoundManager.pausePlayback()
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(
      !mSoundPool.wasResourcePlayed(R.raw.train_horn)
        && !mSoundPool.wasResourcePlayed(R.raw.moving_train)
        && mSoundManager.isPaused()
    )

    mSoundManager.resumePlayback()
    assert(
      mSoundPool.wasResourcePlayed(R.raw.train_horn)
        && mSoundPool.wasResourcePlayed(R.raw.moving_train)
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

    mSoundManager.play("train_horn")
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
    mSoundManager.play("train_horn")
    assert(!isCalled)
  }

  @Test
  fun `should get playing state of a sound`() {
    assert(!mSoundManager.isPlaying("train_horn"))

    mSoundManager.play("train_horn")
    assert(
      mSoundPool.wasResourcePlayed(R.raw.train_horn)
        && mSoundManager.isPlaying("train_horn")
    )

    mSoundManager.stopPlayback()
    assert(!mSoundManager.isPlaying("train_horn"))
  }

  @Test
  fun `should get and set volume`() {
    assert(mSoundManager.getVolume("train_horn") == 4)

    mSoundManager.setVolume("train_horn", 15)
    assert(mSoundManager.getVolume("train_horn") == 15)
  }

  @Test
  fun `should get and set time period`() {
    assert(mSoundManager.getTimePeriod("train_horn") == 60)

    mSoundManager.setTimePeriod("train_horn", 120)
    assert(mSoundManager.getTimePeriod("train_horn") == 120)
  }

  @Test
  fun `should return null preset if playback is paused or stopped`() {
    assert(!mSoundManager.isPlaying && mSoundManager.getCurrentPreset() == null)
    mSoundManager.play("moving_train")
    mSoundManager.pausePlayback()
    assert(mSoundPool.wasResourcePlayed(R.raw.moving_train) && mSoundManager.getCurrentPreset() == null)
  }

  @Test
  fun `should return current preset if playback is on`() {
    mSoundManager.play("moving_train")
    mSoundManager.play("train_horn")
    assert(mSoundPool.wasResourcePlayed(R.raw.moving_train) && mSoundPool.wasResourcePlayed(R.raw.train_horn))

    val preset = mSoundManager.getCurrentPreset()!!
    assert(preset.playbackStates.size == 2)
    assert(
      preset.playbackStates.contains(PresetFragment.Preset.PresetPlaybackState("moving_train", 0.2f, 60))
        && preset.playbackStates.contains(PresetFragment.Preset.PresetPlaybackState("train_horn", 0.2f, 60))
    )
  }

  @Test
  fun `should correctly play given preset`() {
    mSoundManager.playPreset(
      PresetFragment.Preset(
        "random", arrayOf(
          PresetFragment.Preset.PresetPlaybackState("train_horn", 0.2f, 60),
          PresetFragment.Preset.PresetPlaybackState("water_stream", 0.2f, 60)
        )
      )
    )

    assert(mSoundPool.wasResourcePlayed(R.raw.train_horn) && mSoundPool.wasResourcePlayed(R.raw.water_stream))
  }
}

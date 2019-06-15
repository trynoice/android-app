package com.github.ashutoshgngwr.noice

import android.media.AudioAttributes
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
    mSoundManager.play(R.raw.moving_train)
    assert(mSoundManager.isPlaying && !mSoundManager.isPaused())
    assert(mSoundPool.wasResourcePlayed(R.raw.moving_train))
  }

  @Test
  fun `should play non-loopable sound`() {
    mSoundManager.play(R.raw.train_horn)
    assert(mSoundManager.isPlaying && !mSoundManager.isPaused())
    assert(
      mSoundPool.wasResourcePlayed(R.raw.train_horn)
        && mSoundManager.isPlaying(R.raw.train_horn)
    )

    mSoundPool.clearPlayed()

    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(
      mSoundPool.wasResourcePlayed(R.raw.train_horn)
        && mSoundManager.isPlaying(R.raw.train_horn)
    )
  }

  @Test
  fun `should add newly played sounds to pause state if playback is paused`() {
    mSoundManager.play(R.raw.moving_train)
    assert(mSoundManager.isPlaying && !mSoundManager.isPaused())

    mSoundManager.pausePlayback()
    mSoundManager.play(R.raw.water_stream)
    mSoundManager.play(R.raw.moving_train) // playing twice shouldn't affect pause state
    assert(!mSoundManager.isPlaying && mSoundManager.isPaused())

    mSoundPool.clearPlayed()
    mSoundManager.resumePlayback()
    assert(mSoundManager.isPlaying && !mSoundManager.isPaused())
    assert(mSoundPool.wasResourcePlayed(R.raw.moving_train) && mSoundPool.wasResourcePlayed(R.raw.water_stream))
  }

  @Test
  fun `should stop loopable sounds`() {
    mSoundManager.play(R.raw.moving_train)
    mSoundManager.play(R.raw.water_stream)
    assert(mSoundPool.wasResourcePlayed(R.raw.moving_train))

    mSoundPool.clearPlayed()

    mSoundManager.stop(R.raw.moving_train)
    mSoundManager.stop(R.raw.water_stream)
    assert(
      !mSoundPool.wasResourcePlayed(R.raw.moving_train)
        && !mSoundPool.wasResourcePlayed(R.raw.water_stream)
    )
  }

  @Test
  fun `should stop non-loopable sound`() {
    // stopping a stopped sound should have no effect
    mSoundManager.stop(R.raw.train_horn)
    assert(!mSoundManager.isPlaying(R.raw.train_horn))

    mSoundManager.play(R.raw.train_horn)
    assert(mSoundPool.wasResourcePlayed(R.raw.train_horn))

    mSoundPool.clearPlayed()

    mSoundManager.stop(R.raw.train_horn)
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    assert(!mSoundPool.wasResourcePlayed(R.raw.train_horn))
  }

  @Test
  fun `should stop playback`() {
    // stopPlayback without playback state should have no effect
    mSoundManager.stopPlayback()
    assert(!mSoundManager.isPlaying && !mSoundManager.isPaused())

    mSoundManager.play(R.raw.train_horn)
    mSoundManager.play(R.raw.moving_train)
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

    mSoundManager.play(R.raw.train_horn)
    mSoundManager.play(R.raw.moving_train)
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

    mSoundManager.play(R.raw.train_horn)
    mSoundManager.play(R.raw.moving_train)
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

    mSoundManager.play(R.raw.train_horn)
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
    mSoundManager.play(R.raw.train_horn)
    assert(!isCalled)
  }

  @Test
  fun `should get playing state of a sound`() {
    assert(!mSoundManager.isPlaying(R.raw.train_horn))

    mSoundManager.play(R.raw.train_horn)
    assert(
      mSoundPool.wasResourcePlayed(R.raw.train_horn)
        && mSoundManager.isPlaying(R.raw.train_horn)
    )

    mSoundManager.stopPlayback()
    assert(!mSoundManager.isPlaying(R.raw.train_horn))
  }

  @Test
  fun `should get and set volume`() {
    assert(mSoundManager.getVolume(R.raw.train_horn) == 4)

    mSoundManager.setVolume(R.raw.train_horn, 15)
    assert(mSoundManager.getVolume(R.raw.train_horn) == 15)
  }

  @Test
  fun `should get and set time period`() {
    assert(mSoundManager.getTimePeriod(R.raw.train_horn) == 60)

    mSoundManager.setTimePeriod(R.raw.train_horn, 120)
    assert(mSoundManager.getTimePeriod(R.raw.train_horn) == 120)
  }
}

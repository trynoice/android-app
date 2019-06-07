package com.github.ashutoshgngwr.noice

import android.content.Intent
import android.media.AudioManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
class MediaPlayerServiceTest {

  private lateinit var serviceController: ServiceController<MediaPlayerService>

  @Before
  fun setup() {
    serviceController = Robolectric.buildService(MediaPlayerService::class.java).create()
  }

  @Test
  fun `should not be in foreground if playback is not started`() {
    serviceController
      .get()
      .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder
    assert(shadowOf(serviceController.get()).lastForegroundNotification == null)
  }

  @Test
  fun `should come to foreground on media playback`() {
    val binder = serviceController
      .get()
      .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder

    binder.getSoundManager().play(R.raw.moving_train)
    assert(shadowOf(serviceController.get()).lastForegroundNotificationId == MediaPlayerService.FOREGROUND_ID)
  }

  @Test
  fun `should not be in foreground if playback is stopped`() {
    val binder = serviceController
      .get()
      .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder

    binder.getSoundManager().play(R.raw.moving_train)
    binder.getSoundManager().stop()

    assert(shadowOf(serviceController.get()).isForegroundStopped)
  }

  @Test
  fun `should send pause playback intent on notification pause action click`() {
    val binder = serviceController
      .get()
      .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder

    binder.getSoundManager().play(R.raw.moving_train)

    val contentIntent = shadowOf(
      shadowOf(serviceController.get())
        .lastForegroundNotification
        .actions[1]
        .actionIntent
    ).savedIntent

    assert(contentIntent.getIntExtra("action", 0) == MediaPlayerService.RC_STOP_PLAYBACK)
  }

  @Test
  fun `should send resume playback intent on notification play action click`() {
    val binder = serviceController
      .get()
      .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder

    binder.getSoundManager().play(R.raw.moving_train)
    binder.getSoundManager().pausePlayback()

    val contentIntent = shadowOf(
      shadowOf(serviceController.get())
        .lastForegroundNotification
        .actions[1]
        .actionIntent
    ).savedIntent

    assert(contentIntent.getIntExtra("action", 0) == MediaPlayerService.RC_START_PLAYBACK)
  }

  @Test
  fun `should send stop service intent on notification stop action click`() {
    val binder = serviceController
      .get()
      .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder

    binder.getSoundManager().play(R.raw.moving_train)

    val contentIntent = shadowOf(
      shadowOf(serviceController.get())
        .lastForegroundNotification
        .actions[0]
        .actionIntent
    ).savedIntent

    assert(contentIntent.getIntExtra("action", 0) == MediaPlayerService.RC_STOP_SERVICE)
  }

  @Test
  fun `should resume playback on receiving start playback intent`() {
    serviceController = Robolectric.buildService(
      MediaPlayerService::class.java,
      Shadow.newInstanceOf(Intent::class.java)
        .putExtra("action", MediaPlayerService.RC_START_PLAYBACK)
    ).create()

    val binder = serviceController
      .get()
      .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder

    binder.getSoundManager().play(R.raw.moving_train)
    binder.getSoundManager().pausePlayback()
    serviceController.startCommand(0, 0)
    assert(!binder.getSoundManager().isPaused() && binder.getSoundManager().isPlaying)
  }

  @Test
  fun `should pause playback on receiving stop playback intent`() {
    serviceController = Robolectric.buildService(
      MediaPlayerService::class.java,
      Shadow.newInstanceOf(Intent::class.java)
        .putExtra("action", MediaPlayerService.RC_STOP_PLAYBACK)
    ).create()

    val binder = serviceController
      .get()
      .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder

    binder.getSoundManager().play(R.raw.moving_train)
    serviceController.startCommand(0, 0)
    assert(binder.getSoundManager().isPaused() && !binder.getSoundManager().isPlaying)
  }

  @Test
  fun `should exit foreground on receiving stop service intent`() {
    serviceController = Robolectric.buildService(
      MediaPlayerService::class.java,
      Shadow.newInstanceOf(Intent::class.java)
        .putExtra("action", MediaPlayerService.RC_STOP_SERVICE)
    ).create()

    val binder = serviceController
      .get()
      .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder

    binder.getSoundManager().play(R.raw.moving_train)
    serviceController.startCommand(0, 0)
    assert(!binder.getSoundManager().isPlaying)
    assert(shadowOf(serviceController.get()).isForegroundStopped)
  }

  @Test
  fun `should pause playback on receiving becoming noisy broadcast`() {
    val binder = serviceController
      .get()
      .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder

    val mSoundPoolShadow = shadowOf(binder.getSoundManager().mSoundPool)
    binder.getSoundManager().play(R.raw.train_horn)
    assert(
      mSoundPoolShadow.wasResourcePlayed(R.raw.train_horn)
        && binder.getSoundManager().isPlaying
    )

    mSoundPoolShadow.clearPlayed()
    RuntimeEnvironment
      .systemContext
      .sendBroadcast(Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

    assert(
      !mSoundPoolShadow.wasResourcePlayed(R.raw.train_horn)
        && binder.getSoundManager().isPaused()
    )
  }

  @Test
  fun `should handle audio focus changes`() {
    val binder = serviceController
      .get()
      .onBind(Shadow.newInstanceOf(Intent::class.java)) as MediaPlayerService.PlaybackBinder

    val mSoundPoolShadow = shadowOf(binder.getSoundManager().mSoundPool)
    binder.getSoundManager().play(R.raw.moving_train)
    assert(
      mSoundPoolShadow.wasResourcePlayed(R.raw.moving_train)
        && binder.getSoundManager().isPlaying
    )

    serviceController.get().onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
    assert(binder.getSoundManager().isPaused())

    mSoundPoolShadow.clearPlayed()
    serviceController.get().onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
    assert(
      mSoundPoolShadow.wasResourcePlayed(R.raw.moving_train)
        && binder.getSoundManager().isPlaying
    )

    serviceController.get().onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
    assert(binder.getSoundManager().isPaused())
  }

  @Test
  fun `should not fail on destroy`() {
    serviceController.create().destroy()
  }
}

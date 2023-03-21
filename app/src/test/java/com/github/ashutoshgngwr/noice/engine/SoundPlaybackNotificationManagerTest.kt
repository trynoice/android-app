package com.github.ashutoshgngwr.noice.engine

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.StubService
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationManager

@RunWith(RobolectricTestRunner::class)
class SoundPlaybackNotificationManagerTest {

  private lateinit var mediaSessionToken: MediaSessionCompat.Token
  private lateinit var contentPiMock: PendingIntent
  private lateinit var resumePiMock: PendingIntent
  private lateinit var pausePiMock: PendingIntent
  private lateinit var stopPiMock: PendingIntent
  private lateinit var randomPresetPiMock: PendingIntent
  private lateinit var skipToNextPresetPiMock: PendingIntent
  private lateinit var skipToPrevPresetPiMock: PendingIntent
  private lateinit var manager: SoundPlaybackNotificationManager
  private lateinit var notificationManagerShadow: ShadowNotificationManager

  @Before
  fun setUp() {
    val service = Robolectric.buildService(StubService::class.java).get()
    val mediaSession = MediaSessionCompat(service, "test-media-session") // mockk fails
    mediaSessionToken = mediaSession.sessionToken
    contentPiMock = mockk(relaxed = true)
    resumePiMock = mockk(relaxed = true)
    pausePiMock = mockk(relaxed = true)
    stopPiMock = mockk(relaxed = true)
    randomPresetPiMock = mockk(relaxed = true)
    skipToNextPresetPiMock = mockk(relaxed = true)
    skipToPrevPresetPiMock = mockk(relaxed = true)
    manager = SoundPlaybackNotificationManager(
      service = service,
      mediaSessionToken = mediaSessionToken,
      contentPi = contentPiMock,
      resumePi = resumePiMock,
      pausePi = pausePiMock,
      stopPi = stopPiMock,
      randomPresetPi = randomPresetPiMock,
      skipToNextPresetPi = skipToNextPresetPiMock,
      skipToPrevPresetPi = skipToPrevPresetPiMock,
    )

    notificationManagerShadow = service.getSystemService<NotificationManager>()
      .also { requireNotNull(it) }
      .let { shadowOf(it) }
  }

  @Test
  fun setState() {
    data class TestCase(
      val managerState: SoundPlayerManager.State,
      val expectedContentText: String?,
      val expectedActionPis: List<PendingIntent>?,
    )

    val context = ApplicationProvider.getApplicationContext<Context>()
    listOf(
      TestCase(
        managerState = SoundPlayerManager.State.PLAYING,
        expectedContentText = context.getString(R.string.playing),
        expectedActionPis = listOf(pausePiMock, stopPiMock),
      ),
      TestCase(
        managerState = SoundPlayerManager.State.PAUSING,
        expectedContentText = context.getString(R.string.pausing),
        expectedActionPis = listOf(resumePiMock, stopPiMock),
      ),
      TestCase(
        managerState = SoundPlayerManager.State.PAUSED,
        expectedContentText = context.getString(R.string.paused),
        expectedActionPis = listOf(resumePiMock, stopPiMock),
      ),
      TestCase(
        managerState = SoundPlayerManager.State.STOPPING,
        expectedContentText = context.getString(R.string.stopping),
        expectedActionPis = listOf(resumePiMock),
      ),
      TestCase(
        managerState = SoundPlayerManager.State.STOPPED,
        expectedContentText = null,
        expectedActionPis = null,
      ),
    ).forEach { testCase ->
      manager.setState(testCase.managerState)
      val notification = notificationManagerShadow
        .getNotification(SoundPlaybackNotificationManager.NOTIFICATION_ID)

      if (testCase.expectedContentText == null || testCase.expectedActionPis == null) {
        assertNull(notification)
      } else {
        assertNotNull(notification)
        assertEquals(testCase.expectedContentText, shadowOf(notification).contentText)
        assertEquals(contentPiMock, notification.contentIntent)
        testCase.expectedActionPis.forEach { pi ->
          assertTrue(notification.actions.any { it.actionIntent == pi })
        }
      }
    }
  }

  @Test
  fun setCurrentPresetName() {
    data class TestCase(
      val presetName: String?,
      val expectedContentTitle: String,
      val expectedActionPis: List<PendingIntent>,
    )

    val context = ApplicationProvider.getApplicationContext<Context>()
    listOf(
      TestCase(
        presetName = "test-preset-1",
        expectedContentTitle = "test-preset-1",
        expectedActionPis = listOf(skipToPrevPresetPiMock, skipToNextPresetPiMock),
      ),
      TestCase(
        presetName = "test-preset-2",
        expectedContentTitle = "test-preset-2",
        expectedActionPis = listOf(skipToPrevPresetPiMock, skipToNextPresetPiMock),
      ),
      TestCase(
        presetName = null,
        expectedContentTitle = context.getString(R.string.unsaved_preset),
        expectedActionPis = listOf(randomPresetPiMock),
      )
    ).forEach { testCase ->
      manager.setState(SoundPlayerManager.State.PLAYING)
      manager.setCurrentPresetName(testCase.presetName)
      val notification = notificationManagerShadow
        .getNotification(SoundPlaybackNotificationManager.NOTIFICATION_ID)

      assertNotNull(notification)
      assertEquals(testCase.expectedContentTitle, shadowOf(notification).contentTitle)
      assertEquals(contentPiMock, notification.contentIntent)
      testCase.expectedActionPis.forEach { pi ->
        assertTrue(notification.actions.any { it.actionIntent == pi })
      }
    }
  }
}

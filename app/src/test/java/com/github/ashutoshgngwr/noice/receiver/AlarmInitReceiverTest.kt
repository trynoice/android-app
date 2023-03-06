package com.github.ashutoshgngwr.noice.receiver

import android.app.AlarmManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.di.AlarmRepositoryModule
import com.github.ashutoshgngwr.noice.repository.AlarmRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.called
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@HiltAndroidTest
@UninstallModules(AlarmRepositoryModule::class)
@RunWith(RobolectricTestRunner::class)
class AlarmInitReceiverTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @BindValue
  internal lateinit var alarmRepositoryMock: AlarmRepository

  private lateinit var receiver: AlarmInitReceiver

  @Before
  fun setUp() {
    alarmRepositoryMock = mockk(relaxed = true)
    receiver = AlarmInitReceiver()
  }

  @Test
  fun onReceive() {
    mapOf(
      AlarmInitReceiver.ACTION_INIT to true,
      AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED to true,
      Intent.ACTION_BOOT_COMPLETED to true,
      "android.intent.action.QUICKBOOT_POWERON" to true,
      Intent.ACTION_TIME_CHANGED to true,
      Intent.ACTION_TIMEZONE_CHANGED to true,
      "malformed-action" to false,
    ).forEach { (action, shouldUpdateAlarms) ->
      for (canScheduleAlarms in arrayOf(false, true)) {
        clearMocks(alarmRepositoryMock)
        every { alarmRepositoryMock.canScheduleAlarms() } returns canScheduleAlarms
        receiver.onReceive(ApplicationProvider.getApplicationContext(), Intent(action))

        coVerify(exactly = 1, timeout = 5000) {
          when {
            !shouldUpdateAlarms -> alarmRepositoryMock wasNot called
            canScheduleAlarms -> alarmRepositoryMock.rescheduleAll()
            else -> alarmRepositoryMock.disableAll()
          }
        }
      }
    }
  }
}

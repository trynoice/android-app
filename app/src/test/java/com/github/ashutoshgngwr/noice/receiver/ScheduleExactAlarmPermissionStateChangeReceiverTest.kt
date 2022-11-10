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
class ScheduleExactAlarmPermissionStateChangeReceiverTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @BindValue
  internal lateinit var alarmRepository: AlarmRepository

  private lateinit var receiver: ScheduleExactAlarmPermissionStateChangeReceiver

  @Before
  fun setUp() {
    alarmRepository = mockk(relaxed = true)
    receiver = ScheduleExactAlarmPermissionStateChangeReceiver()
  }

  @Test
  fun permissionRevoked() {
    every { alarmRepository.canScheduleAlarms() } returns false
    receiver.onReceive(
      ApplicationProvider.getApplicationContext(),
      Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
    )

    coVerify { alarmRepository.disableAll() }
  }
}

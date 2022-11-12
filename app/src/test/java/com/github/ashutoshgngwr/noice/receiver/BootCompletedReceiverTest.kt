package com.github.ashutoshgngwr.noice.receiver

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
class BootCompletedReceiverTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @BindValue
  internal lateinit var alarmRepository: AlarmRepository

  private lateinit var receiver: BootCompletedReceiver

  @Before
  fun setUp() {
    alarmRepository = mockk(relaxed = true)
    receiver = BootCompletedReceiver()
  }

  @Test
  fun onBootCompleted() {
    every { alarmRepository.canScheduleAlarms() } returns true
    receiver.onReceive(
      ApplicationProvider.getApplicationContext(),
      Intent(Intent.ACTION_BOOT_COMPLETED)
    )

    coVerify(exactly = 1, timeout = 5000L) { alarmRepository.rescheduleAll() }
  }

  @Test
  fun onQuickBootPowerOn() {
    every { alarmRepository.canScheduleAlarms() } returns true
    receiver.onReceive(
      ApplicationProvider.getApplicationContext(),
      Intent("android.intent.action.QUICKBOOT_POWERON"),
    )

    coVerify(exactly = 1, timeout = 5000L) { alarmRepository.rescheduleAll() }
  }
}

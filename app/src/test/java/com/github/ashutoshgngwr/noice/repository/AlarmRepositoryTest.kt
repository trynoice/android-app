package com.github.ashutoshgngwr.noice.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.data.AlarmDao
import com.github.ashutoshgngwr.noice.data.AppDatabase
import com.github.ashutoshgngwr.noice.data.models.AlarmDto
import com.github.ashutoshgngwr.noice.fragment.AlarmComparator
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.models.Alarm
import com.github.ashutoshgngwr.noice.models.toRoomDto
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.util.*
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes


@RunWith(RobolectricTestRunner::class)
class AlarmRepositoryTest {

  private lateinit var presetRepositoryMock: PresetRepository
  private lateinit var settingsRepositoryMock: SettingsRepository
  private lateinit var alarmDaoMock: AlarmDao
  private lateinit var alarmManagerShadow: ShadowAlarmManager
  private lateinit var repository: AlarmRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    presetRepositoryMock = mockk(relaxed = true)
    settingsRepositoryMock = mockk(relaxed = true)
    alarmDaoMock = mockk(relaxed = true)
    val appDb = mockk<AppDatabase> {
      every { alarms() } returns alarmDaoMock
    }

    val alarmManager = requireNotNull(context.getSystemService<AlarmManager>())
    alarmManagerShadow = shadowOf(alarmManager)
    repository = AlarmRepository(
      alarmManager = alarmManager,
      presetRepository = presetRepositoryMock,
      settingsRepository = settingsRepositoryMock,
      appDb = appDb,
      pendingIntentBuilder = object : AlarmRepository.PendingIntentBuilder {
        override fun buildShowIntent(alarm: Alarm): PendingIntent {
          return buildDummyPendingIntent(context, alarm)
        }

        override fun buildTriggerIntent(alarm: Alarm): PendingIntent {
          return buildDummyPendingIntent(context, alarm)
        }
      },
    )
  }

  @Test
  fun saveAndDelete() = runTest {
    assertNull(alarmManagerShadow.peekNextScheduledAlarm())

    repository.save(buildAlarm(id = 1, minuteOfDay = 120))
    coVerify(exactly = 1) {
      alarmDaoMock.save(buildAlarmDto(id = 1, minuteOfDay = 120))
    }

    var nextAlarm = alarmManagerShadow.peekNextScheduledAlarm()
    assertNotNull(nextAlarm)
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = nextAlarm.triggerAtTime
    assertEquals(2, calendar.get(Calendar.HOUR_OF_DAY))
    assertEquals(1, shadowOf(nextAlarm.operation).requestCode)

    // should cancel and update the registered system alarm
    repository.save(buildAlarm(id = 1, minuteOfDay = 240))
    assertEquals(1, alarmManagerShadow.scheduledAlarms.size)
    nextAlarm = alarmManagerShadow.peekNextScheduledAlarm()
    assertNotNull(nextAlarm)
    calendar.timeInMillis = nextAlarm.triggerAtTime
    assertEquals(4, calendar.get(Calendar.HOUR_OF_DAY))

    repository.delete(buildAlarm(id = 1, minuteOfDay = 360))
    coVerify(exactly = 1) { alarmDaoMock.deleteById(1) }
    assertNull(alarmManagerShadow.peekNextScheduledAlarm())
  }

  @Test
  fun get() = runTest {
    coEvery { alarmDaoMock.getById(1) } returns null
    assertNull(repository.get(1))

    val mockPreset = mockk<Preset>()
    every { presetRepositoryMock.get(any()) } returns mockPreset
    coEvery { alarmDaoMock.getById(1) } returns buildAlarmDto(
      id = 1,
      minuteOfDay = 120,
      presetId = "test-preset-id",
    )

    assertEquals(buildAlarm(id = 1, minuteOfDay = 120, preset = mockPreset), repository.get(1))
  }

  @Test
  fun countEnabled() = runTest {
    coEvery { alarmDaoMock.countEnabledFlow() } returns flowOf(10)
    assertEquals(10, repository.countEnabled().firstOrNull())
  }

  @Test
  fun pagingDataFlow() = runTest {
    val presets = listOf(Preset("preset-1", emptyArray()), Preset("preset-2", emptyArray()))
    val input = listOf(
      buildAlarmDto(id = 1, minuteOfDay = 1, presetId = presets[0].id),
      buildAlarmDto(id = 2, minuteOfDay = 2, presetId = presets[1].id),
      buildAlarmDto(id = 3, minuteOfDay = 3),
    )

    val output = input.map { dto ->
      buildAlarm(
        id = dto.id,
        minuteOfDay = dto.minuteOfDay,
        isEnabled = dto.isEnabled,
        preset = presets.find { it.id == dto.presetId },
      )
    }

    every { presetRepositoryMock.list() } returns presets
    every { alarmDaoMock.pagingSource() } returns object : PagingSource<Int, AlarmDto>() {
      override fun getRefreshKey(state: PagingState<Int, AlarmDto>): Int = 0
      override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AlarmDto> {
        return LoadResult.Page(data = input, nextKey = null, prevKey = null)
      }
    }

    val testDispatcher = StandardTestDispatcher(testScheduler)
    val differ = AsyncPagingDataDiffer(
      diffCallback = AlarmComparator,
      updateCallback = object : ListUpdateCallback {
        override fun onChanged(position: Int, count: Int, payload: Any?) {}
        override fun onMoved(fromPosition: Int, toPosition: Int) {}
        override fun onInserted(position: Int, count: Int) {}
        override fun onRemoved(position: Int, count: Int) {}
      },
      mainDispatcher = testDispatcher,
      workerDispatcher = testDispatcher,
    )

    val job = launch {
      repository.pagingDataFlow()
        .cachedIn(this)
        .collect { differ.submitData(it) }
    }

    try {
      advanceUntilIdle()
      assertEquals(output, differ.snapshot().items)
    } finally {
      job.cancelAndJoin()
    }
  }

  @Test
  fun canScheduleAlarms() {
    val inputs = arrayOf(true, false)
    for (input in inputs) {
      ShadowAlarmManager.setCanScheduleExactAlarms(input)
      assertEquals(input, repository.canScheduleAlarms())
    }
  }

  @Test
  fun reportTrigger() = runTest {
    data class TestCase(
      val alarm: Alarm,
      val isSnoozed: Boolean,
      val expectDisabled: Boolean,
      val nextTriggerAfterMinutes: Int?,
    )

    val snoozeLengthMinutes = 10
    val testCases = listOf(
      TestCase(
        alarm = buildAlarm(id = 1, minuteOfDay = 120, isEnabled = true, weeklySchedule = 0),
        isSnoozed = false,
        expectDisabled = true,
        nextTriggerAfterMinutes = null,
      ),
      TestCase(
        alarm = buildAlarm(id = 2, minuteOfDay = 240, isEnabled = true, weeklySchedule = 0),
        isSnoozed = true,
        expectDisabled = true,
        nextTriggerAfterMinutes = snoozeLengthMinutes,
      ),
      TestCase(
        alarm = buildAlarm(id = 3, minuteOfDay = 360, isEnabled = true, weeklySchedule = 0b1111111),
        isSnoozed = false,
        expectDisabled = false,
        nextTriggerAfterMinutes = Calendar.getInstance()
          .apply { add(Calendar.DAY_OF_MONTH, 1) }
          .apply { set(Calendar.HOUR_OF_DAY, 6) }
          .apply { set(Calendar.MINUTE, 0) }
          .apply { set(Calendar.SECOND, 0) }
          .apply { set(Calendar.MILLISECOND, 0) }
          .timeInMillis
          .let { ceil((it - System.currentTimeMillis()) / 60000.0) }
          .roundToInt(),
      ),
      TestCase(
        alarm = buildAlarm(id = 4, minuteOfDay = 480, isEnabled = true, weeklySchedule = 0b1111111),
        isSnoozed = true,
        expectDisabled = false,
        nextTriggerAfterMinutes = snoozeLengthMinutes,
      ),
    )

    every { settingsRepositoryMock.getAlarmSnoozeDuration() } returns snoozeLengthMinutes.minutes

    testCases.forEachIndexed { index, testCase ->
      alarmManagerShadow.scheduledAlarms.clear()
      clearMocks(alarmDaoMock)

      coEvery { alarmDaoMock.getById(testCase.alarm.id) } returns testCase.alarm.toRoomDto()
      repository.reportTrigger(testCase.alarm.id, testCase.isSnoozed)

      coVerify(exactly = if (testCase.expectDisabled) 1 else 0) {
        alarmDaoMock.save(withArg { it.id == testCase.alarm.id && !it.isEnabled })
      }

      val nextAlarm = alarmManagerShadow.peekNextScheduledAlarm()
      if (testCase.nextTriggerAfterMinutes == null) {
        assertNull("Testcase #$index failed", nextAlarm)
      } else {
        assertNotNull("Testcase #$index failed", nextAlarm)
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nextAlarm.triggerAtTime
        assertEquals(
          "Testcase #$index failed",
          testCase.nextTriggerAfterMinutes,
          ceil((nextAlarm.triggerAtTime - System.currentTimeMillis()) / 60000.0).roundToInt(),
        )
      }
    }
  }

  @Test
  fun rescheduleAll() = runTest {
    val alarms = listOf(
      buildAlarmDto(id = 1, minuteOfDay = 120, isEnabled = true),
      buildAlarmDto(id = 2, minuteOfDay = 240, isEnabled = true),
      buildAlarmDto(id = 3, minuteOfDay = 360, isEnabled = true),
    )

    coEvery { alarmDaoMock.listEnabled() } returns alarms
    repository.rescheduleAll()
    assertEquals(alarms.size, alarmManagerShadow.scheduledAlarms.size)

    val calendar = Calendar.getInstance()
    alarmManagerShadow.scheduledAlarms.forEachIndexed { index, alarm ->
      calendar.timeInMillis = alarm.triggerAtTime
      assertEquals(
        alarms[index].minuteOfDay,
        calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE),
      )
    }
  }

  @Test
  fun disableAll() = runTest {
    val alarms = listOf(
      buildAlarmDto(id = 1, minuteOfDay = 1, isEnabled = true),
      buildAlarmDto(id = 2, minuteOfDay = 2, isEnabled = true),
      buildAlarmDto(id = 3, minuteOfDay = 3, isEnabled = true),
    )

    coEvery { alarmDaoMock.listEnabled() } returns alarms
    val offset = 1
    repository.disableAll(offset)
    alarms.forEachIndexed { index, alarm ->
      coVerify(exactly = if (index < offset) 0 else 1) {
        alarmDaoMock.save(alarm.copy(isEnabled = false))
      }
    }
  }

  private fun buildDummyPendingIntent(context: Context, alarm: Alarm? = null): PendingIntent {
    return PendingIntent.getBroadcast(
      context,
      alarm?.id ?: 0x1f,
      Intent("dummyAction"),
      PendingIntent.FLAG_UPDATE_CURRENT,
    )
  }

  private fun buildAlarm(
    id: Int,
    minuteOfDay: Int,
    isEnabled: Boolean = true,
    weeklySchedule: Int = 0,
    preset: Preset? = null,
  ): Alarm {
    return Alarm(
      id = id,
      label = null,
      minuteOfDay = minuteOfDay,
      isEnabled = isEnabled,
      weeklySchedule = weeklySchedule,
      vibrate = true,
      preset = preset,
    )
  }

  private fun buildAlarmDto(
    id: Int,
    minuteOfDay: Int,
    isEnabled: Boolean = true,
    weeklySchedule: Int = 0,
    presetId: String? = null,
  ): AlarmDto {
    return AlarmDto(
      id = id,
      label = null,
      minuteOfDay = minuteOfDay,
      isEnabled = isEnabled,
      weeklySchedule = weeklySchedule,
      vibrate = true,
      presetId = presetId,
    )
  }
}

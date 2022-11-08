package com.github.ashutoshgngwr.noice.data

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.data.models.AlarmDto
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AlarmDaoTest {

  private lateinit var appDb: AppDatabase
  private lateinit var alarmDao: AlarmDao

  @Before
  fun setUp() {
    appDb = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      AppDatabase::class.java,
    ).build()

    alarmDao = appDb.alarms()
  }

  @After
  fun tearDown() {
    appDb.close()
  }

  @Test
  fun saveThenGetByIdAndDeleteById() = runTest {
    val alarm1 = buildAlarm(id = 1, minuteOfDay = 120, weeklySchedule = 0, isEnabled = false)
    val alarm2 = buildAlarm(id = 2, minuteOfDay = 240, weeklySchedule = 1, isEnabled = true)
    alarmDao.save(alarm1)
    alarmDao.save(alarm2)
    assertEquals(alarm1, alarmDao.getById(alarm1.id))
    assertEquals(alarm2, alarmDao.getById(alarm2.id))

    alarmDao.deleteById(alarm1.id)
    assertNull(alarmDao.getById(alarm1.id))
    assertNotNull(alarmDao.getById(alarm2.id))
  }

  @Test
  fun saveThenListEnabledAndCountEnabled() = runTest {
    val alarm1 = buildAlarm(id = 1, minuteOfDay = 120, weeklySchedule = 0, isEnabled = false)
    val alarm2 = buildAlarm(id = 2, minuteOfDay = 240, weeklySchedule = 1, isEnabled = true)
    alarmDao.save(alarm1)
    alarmDao.save(alarm2)

    val enabled = alarmDao.listEnabled()
    assertEquals(1, enabled.size)
    assertEquals(alarm2, enabled.firstOrNull())
    assertEquals(1, alarmDao.countEnabledFlow().firstOrNull())
  }

  @Test
  fun saveAndPagingSource() = runTest {
    val alarm1 = buildAlarm(id = 1, minuteOfDay = 120, weeklySchedule = 0, isEnabled = false)
    val alarm2 = buildAlarm(id = 2, minuteOfDay = 240, weeklySchedule = 1, isEnabled = true)
    alarmDao.save(alarm1)
    alarmDao.save(alarm2)

    val pagingData = alarmDao.pagingSource()
      .load(PagingSource.LoadParams.Refresh(null, 3, false))
      .let { it as? PagingSource.LoadResult.Page }
      ?.data

    assertEquals(pagingData, listOf(alarm1, alarm2))
  }

  private fun buildAlarm(
    id: Int,
    minuteOfDay: Int,
    weeklySchedule: Int,
    isEnabled: Boolean,
  ): AlarmDto {
    return AlarmDto(
      id = id,
      label = null,
      minuteOfDay = minuteOfDay,
      isEnabled = isEnabled,
      weeklySchedule = weeklySchedule,
      vibrate = true,
      presetId = null,
    )
  }
}

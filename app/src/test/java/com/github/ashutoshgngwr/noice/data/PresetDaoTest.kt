package com.github.ashutoshgngwr.noice.data

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.data.models.DefaultPresetsSyncVersionDto
import com.github.ashutoshgngwr.noice.data.models.PresetDto
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PresetDaoTest {

  private lateinit var appDb: AppDatabase
  private lateinit var presetDao: PresetDao

  private val presets = listOf(
    PresetDto(
      id = "test-preset-1",
      name = "test-name-1",
      soundStatesJson = """{"crickets": 1.0, "water_stream": 0.32}"""
    ),
    PresetDto(
      id = "test-preset-2",
      name = "test-name-2",
      soundStatesJson = """{"rain": 0.8, "thunder": 0.8}"""
    ),
    PresetDto(
      id = "test-preset-3",
      name = "test-name-3",
      soundStatesJson = """{"seashore": 0.48, "wind_through_palm_trees": 0.52}"""
    ),
    PresetDto(
      id = "test-preset-4",
      name = "test-name-4",
      soundStatesJson = """{"seagulls": 0.24, "soft_wind": 0.24}"""
    ),
    PresetDto(
      id = "test-preset-5",
      name = "test-name-5",
      soundStatesJson = """{"campfire": 0.88, "night": 0.24, "wolves": 0.12}"""
    ),
  )

  @Before
  fun setUp() {
    appDb = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      AppDatabase::class.java,
    ).build()
    presetDao = appDb.presets()
  }

  @After
  fun tearDown() {
    appDb.close()
  }

  @Test
  fun saveThenDelete() = runTest {
    presets.forEach { presetDao.save(it) }
    assertEquals(presets, presetDao.list())

    val deleted = presets.random()
    presetDao.deleteById(deleted.id)
    assertNull(presetDao.getById(deleted.id))
    assertEquals(presets - deleted, presetDao.list())

    presetDao.deleteAll()
    assertTrue(presetDao.list().isEmpty())
    presets.forEach { assertNull(presetDao.getById(it.id)) }
  }

  @Test
  fun saveThenGet() = runTest {
    presets.forEach { presetDao.save(it) }
    presets.forEach { assertEquals(it, presetDao.getById(it.id)) }
    assertTrue(presets.contains(presetDao.getRandom()))
    presets.forEach {
      assertEquals(it, presetDao.getBySoundStatesJsonFlow(it.soundStatesJson).firstOrNull())
    }

    assertEquals(presets[1], presetDao.getNextOrderedByName(presets[0].name))
    assertNull(presetDao.getNextOrderedByName(presets[4].name))
    assertEquals(presets[3], presetDao.getNextOrderedByName(presets[2].name))

    assertNull(presetDao.getPreviousOrderedByName(presets[0].name))
    assertEquals(presets[3], presetDao.getPreviousOrderedByName(presets[4].name))
    assertEquals(presets[1], presetDao.getPreviousOrderedByName(presets[2].name))

    assertEquals(presets[0], presetDao.getFirstOrderedByName())
    assertEquals(presets[4], presetDao.getLastOrderedByName())
  }

  @Test
  fun saveThenList() = runTest {
    presets.forEach { presetDao.save(it) }
    assertEquals(presets, presetDao.list())
    assertEquals(presets, presetDao.listFlow().firstOrNull())
  }

  @Test
  fun saveThenPagingSource() = runTest {
    presets.forEach { presetDao.save(it) }
    assertEquals(
      presets,
      presetDao.pagingSource("%")
        .load(PagingSource.LoadParams.Refresh(null, presets.size, false))
        .let { it as? PagingSource.LoadResult.Page }
        ?.data,
    )

    val random = presets.random()
    assertEquals(
      presetDao.pagingSource("%${random.name}%")
        .load(PagingSource.LoadParams.Refresh(null, presets.size, false))
        .let { it as? PagingSource.LoadResult.Page }
        ?.data,
      listOf(random),
    )
  }

  @Test
  fun saveThenCountByName() = runTest {
    presets.forEach { presetDao.save(it) }
    val random = presets.random()
    assertEquals(1, presetDao.countByName(random.name))
    assertEquals(0, presetDao.countByName("does not exist"))
  }

  @Test
  fun saveAndGetDefaultPresetsSyncVersion() = runTest {
    assertNull(presetDao.getDefaultPresetsSyncedVersion())
    presetDao.saveDefaultPresetsSyncVersion(DefaultPresetsSyncVersionDto(10))
    assertEquals(10, presetDao.getDefaultPresetsSyncedVersion())
  }
}

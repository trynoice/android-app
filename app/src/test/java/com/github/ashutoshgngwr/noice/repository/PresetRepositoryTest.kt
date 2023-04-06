package com.github.ashutoshgngwr.noice.repository

import android.net.Uri
import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.AppDispatchers
import com.github.ashutoshgngwr.noice.data.PresetDao
import com.github.ashutoshgngwr.noice.data.models.DefaultPresetsSyncVersionDto
import com.github.ashutoshgngwr.noice.data.models.PresetDto
import com.github.ashutoshgngwr.noice.models.Preset
import com.github.ashutoshgngwr.noice.models.PresetV2
import com.google.gson.Gson
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class PresetRepositoryTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  private val presetDtos = listOf(
    PresetDto(
      id = "test-preset-1",
      name = "test-name-1",
      soundStatesJson = """{"crickets":1.0,"water_stream":0.32}"""
    ),
    PresetDto(
      id = "test-preset-2",
      name = "test-name-2",
      soundStatesJson = """{"rain":0.8,"thunder":0.8}"""
    ),
    PresetDto(
      id = "test-preset-3",
      name = "test-name-3",
      soundStatesJson = """{"seashore":0.48,"wind_through_palm_trees":0.52}"""
    ),
  )

  private val presets = listOf(
    Preset(
      id = "test-preset-1",
      name = "test-name-1",
      soundStates = sortedMapOf(
        "crickets" to 1F,
        "water_stream" to 0.32F,
      ),
    ),
    Preset(
      id = "test-preset-2",
      name = "test-name-2",
      soundStates = sortedMapOf(
        "rain" to 0.8F,
        "thunder" to 0.8F,
      ),
    ),
    Preset(
      id = "test-preset-3",
      name = "test-name-3",
      soundStates = sortedMapOf(
        "seashore" to 0.48F,
        "wind_through_palm_trees" to 0.52F,
      ),
    ),
  )

  @set:Inject
  internal lateinit var gson: Gson

  private lateinit var presetDaoMock: PresetDao
  private lateinit var soundRepositoryMock: SoundRepository

  @Before
  fun setUp() {
    mockkStatic("androidx.room.RoomDatabaseKt")

    hiltRule.inject()
    presetDaoMock = mockk(relaxed = true)
    soundRepositoryMock = mockk()
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun save() = runTest {
    val preset = presets.random()
    val repository = presetRepository(this)

    try {
      repository.save(preset.copy(id = ""))
      throw Exception("should not reach here")
    } catch (e: Throwable) {
      assertTrue(e is IllegalArgumentException)
    }

    repository.save(preset)
    coVerify(exactly = 1) {
      presetDaoMock.save(withArg { dto ->
        assertEquals(preset.id, dto.id)
        assertEquals(preset.name, dto.name)
        assertEquals(gson.toJson(preset.soundStates), dto.soundStatesJson)
      })
    }
  }

  @Test
  fun delete() = runTest {
    val preset = presets.random()
    val repository = presetRepository(this)
    repository.delete(preset.id)
    coVerify(exactly = 1) { presetDaoMock.deleteById(preset.id) }
  }

  @Test
  fun pagingDataFlow() = runTest {
    every { presetDaoMock.pagingSource(any()) } answers {
      val nameFilter = firstArg<String>().removePrefix("%").removeSuffix("%")
      object : PagingSource<Int, PresetDto>() {
        override fun getRefreshKey(state: PagingState<Int, PresetDto>): Int = 0
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, PresetDto> {
          return LoadResult.Page(
            data = presetDtos.filter { it.name.contains(nameFilter, ignoreCase = true) },
            nextKey = null,
            prevKey = null,
          )
        }
      }
    }

    val testDispatcher = StandardTestDispatcher(testScheduler)
    listOf("", "does not exists", "test-name-2").forEach { nameFilter ->
      val differ = AsyncPagingDataDiffer(
        diffCallback = object : DiffUtil.ItemCallback<Preset>() {
          override fun areItemsTheSame(oldItem: Preset, newItem: Preset): Boolean {
            return oldItem.id == newItem.id
          }

          override fun areContentsTheSame(oldItem: Preset, newItem: Preset): Boolean {
            return oldItem == newItem
          }
        },
        updateCallback = object : ListUpdateCallback {
          override fun onChanged(position: Int, count: Int, payload: Any?) {}
          override fun onMoved(fromPosition: Int, toPosition: Int) {}
          override fun onInserted(position: Int, count: Int) {}
          override fun onRemoved(position: Int, count: Int) {}
        },
        mainDispatcher = testDispatcher,
        workerDispatcher = testDispatcher,
      )

      val repository = presetRepository(this)
      val job = launch {
        repository.pagingDataFlow(nameFilter)
          .cachedIn(this)
          .collect { differ.submitData(it) }
      }

      try {
        advanceUntilIdle()
        assertEquals(
          presets.filter { it.name.contains(nameFilter, ignoreCase = true) },
          differ.snapshot().items,
        )
      } finally {
        job.cancelAndJoin()
      }
    }
  }

  @Test
  fun get() = runTest {
    coEvery { presetDaoMock.getById(any()) } answers { presetDtos.find { it.id == firstArg() } }

    val repository = presetRepository(this)
    assertNull(repository.get("does not exists"))

    val expected = presets.random()
    val actual = repository.get(expected.id)
    assertEquals(expected, actual)
  }

  @Test
  fun getBySoundStatesFlow() = runTest {
    every {
      presetDaoMock.getBySoundStatesJsonFlow(any())
    } answers {
      flowOf(presetDtos.find { it.soundStatesJson == firstArg() })
    }

    val repository = presetRepository(this)
    assertNull(repository.getBySoundStatesFlow(sortedMapOf("does not exist" to 0.8F)).firstOrNull())

    val expected = presets.random()
    val actual = repository.getBySoundStatesFlow(expected.soundStates).firstOrNull()
    assertEquals(expected, actual)
  }

  @Test
  fun getRandom() = runTest {
    val randomIndex = presetDtos.indices.random()
    coEvery { presetDaoMock.getRandom() } returns null andThen presetDtos[randomIndex]

    val repository = presetRepository(this)
    assertNull(repository.getRandom())

    val expected = presets[randomIndex]
    val actual = repository.getRandom()
    assertEquals(expected, actual)
  }

  @Test
  fun getNextPreset() = runTest {
    val currentIndex = 1
    coEvery {
      presetDaoMock.getNextOrderedByName(any())
    } returns null andThen null andThen presetDtos[currentIndex + 1]

    coEvery {
      presetDaoMock.getFirstOrderedByName()
    } returns presetDtos.first() andThen null

    val repository = presetRepository(this)
    assertEquals(presets.first(), repository.getNextPreset(presets.last()))
    assertNull(repository.getNextPreset(presets.last()))
    assertEquals(presets[currentIndex + 1], repository.getNextPreset(presets[currentIndex]))
  }

  @Test
  fun getPreviousPreset() = runTest {
    val currentIndex = 1
    coEvery {
      presetDaoMock.getPreviousOrderedByName(any())
    } returns null andThen null andThen presetDtos[currentIndex - 1]

    coEvery {
      presetDaoMock.getLastOrderedByName()
    } returns presetDtos.last() andThen null

    val repository = presetRepository(this)
    assertEquals(presets.last(), repository.getPreviousPreset(presets.first()))
    assertNull(repository.getPreviousPreset(presets.first()))
    assertEquals(presets[currentIndex - 1], repository.getPreviousPreset(presets[currentIndex]))
  }

  @Test
  fun existsByName() = runTest {
    coEvery { presetDaoMock.countByName("test-preset-1") } returns 0
    coEvery { presetDaoMock.countByName("test-preset-2") } returns 1

    val repository = presetRepository(this)
    assertEquals(false, repository.existsByName("test-preset-1"))
    assertEquals(true, repository.existsByName("test-preset-2"))
  }

  @Test
  fun generate() = runTest {
    every { soundRepositoryMock.listInfo() } returns flow {
      emit(Resource.Loading(null))
      emit(Resource.Failure(Exception("test-error")))
    }

    val repository = presetRepository(this)
    var resources = repository.generate(emptySet(), 3).toList()
    assertTrue(resources.first() is Resource.Loading)
    assertTrue(resources.last() is Resource.Failure)

    every { soundRepositoryMock.listInfo() } returns flow {
      emit(Resource.Loading(null))
      emit(Resource.Success(listOf(mockk(relaxed = true), mockk(relaxed = true))))
    }

    resources = repository.generate(emptySet(), 3).toList()
    assertTrue(resources.first() is Resource.Loading)
    assertTrue(resources.last() is Resource.Success)
    assertEquals(true, resources.last().data?.soundStates?.isNotEmpty())
  }

  @Test
  fun exportTo_importFrom() = runTest {
    coEvery { presetDaoMock.list() } returns presetDtos
    val repository = presetRepository(this)
    val outputStream = ByteArrayOutputStream()
    repository.exportTo(outputStream)
    repository.importFrom(ByteArrayInputStream(outputStream.toByteArray()))
    presetDtos.forEach { coVerify(exactly = 1) { presetDaoMock.save(it) } }
  }

  @Test
  fun importFrom_v0Export_withV0Presets() = runTest {
    val exportedDataJson = """[{
      "a": "test-name-1",
      "b": [
        { "a": "crickets", "c": 30, "b": 1.0 },
        { "a": "water_stream", "c": 60, "b": 0.32 }
      ]
    },
    {
      "a": "test-name-2",
      "b": [
        { "a": "moderate_rain", "c": 60, "b": 0.8 },
        { "a": "thunder_crack", "c": 40, "b": 0.8 }
      ]
    },
    {
      "a": "test-name-3",
      "b": [
        { "a": "seaside", "c": 67, "b": 0.48 },
        { "a": "wind_in_palm_trees", "c": 43, "b": 0.52 }
      ]
    }]"""

    val exportedJson = gson.toJson(
      mapOf(
        "version" to "presets",
        "data" to exportedDataJson
      )
    )

    val repository = presetRepository(this)
    repository.importFrom(ByteArrayInputStream(exportedJson.toByteArray()))
    presetDtos.forEach { expected ->
      coVerify(exactly = 1) {
        presetDaoMock.save(withArg { actual ->
          assertEquals(expected.name, actual.name)
          assertEquals(expected.soundStatesJson, actual.soundStatesJson)
        })
      }
    }
  }

  @Test
  fun importFrom_v0Export_withV1Presets() = runTest {
    val exportedDataJson = """[{
      "id": "test-preset-1",
      "name": "test-name-1",
      "playerStates": [
        { "soundKey": "crickets", "timePeriod": 30, "volume": 25 },
        { "soundKey": "water_stream", "timePeriod": 60, "volume": 8 }
      ]
    },
    {
      "id": "test-preset-2",
      "name": "test-name-2",
      "playerStates": [
        { "soundKey": "moderate_rain", "timePeriod": 60, "volume": 20 },
        { "soundKey": "thunder_crack", "timePeriod": 40, "volume": 20 }
      ]
    },
    {
      "id": "test-preset-3",
      "name": "test-name-3",
      "playerStates": [
        { "soundKey": "seaside", "timePeriod": 67, "volume": 12 },
        { "soundKey": "wind_in_palm_trees", "timePeriod": 42, "volume": 13 }
      ]
    }]"""

    val exportedJson = gson.toJson(
      mapOf(
        "version" to "presets.v1",
        "data" to exportedDataJson
      )
    )

    val repository = presetRepository(this)
    repository.importFrom(ByteArrayInputStream(exportedJson.toByteArray()))
    presetDtos.forEach { dto -> coVerify(exactly = 1) { presetDaoMock.save(dto) } }
  }

  @Test
  fun importFrom_v0Export_withV2Presets() = runTest {
    val exportedDataJson = """[{
      "id": "test-preset-1",
      "name": "test-name-1",
      "playerStates": [
        { "soundId": "crickets", "volume": 25 },
        { "soundId": "water_stream", "volume": 8 }
      ]
    },
    {
      "id": "test-preset-2",
      "name": "test-name-2",
      "playerStates": [
        { "soundId": "rain", "volume": 20 },
        { "soundId": "thunder", "volume": 20 }
      ]
    },
    {
      "id": "test-preset-3",
      "name": "test-name-3",
      "playerStates": [
        { "soundId": "seashore", "volume": 12 },
        { "soundId": "wind_through_palm_trees", "volume": 13 }
      ]
    }]"""

    val exportedJson = gson.toJson(
      mapOf(
        "version" to "presets.v2",
        "data" to exportedDataJson
      )
    )

    val repository = presetRepository(this)
    repository.importFrom(ByteArrayInputStream(exportedJson.toByteArray()))
    presetDtos.forEach { dto -> coVerify(exactly = 1) { presetDaoMock.save(dto) } }
  }

  @Test
  fun readFromUrl() = runTest {
    val index = presetDtos.indices.random()
    val dto = presetDtos[index]
    val urlV1 = "https://trynoice.com/preset?v=1&" +
      "n=${Uri.encode(dto.name)}&" +
      "s=${Uri.encode(dto.soundStatesJson)}"

    val repository = presetRepository(this)
    val preset1 = repository.readFromUrl(urlV1)
    assertEquals(presets[index].name, preset1?.name)
    assertEquals(presets[index].soundStates, preset1?.soundStates)

    val playerStatesJson = presets[index].soundStates
      .map { PresetV2.PlayerState(it.key, (it.value * 25).roundToInt()) }
      .let { gson.toJson(it) }

    val urlPresetV2 = "https://trynoice.com/preset?" +
      "n=${Uri.encode(dto.name)}&" +
      "ps=${Uri.encode(playerStatesJson)}"

    val preset2 = repository.readFromUrl(urlPresetV2)
    assertEquals(presets[index].name, preset2?.name)
    assertEquals(presets[index].soundStates, preset2?.soundStates)

    assertNull(repository.readFromUrl("https://trynoice.com/preset?v=1&n=test"))
    assertNull(repository.readFromUrl("https://trynoice.com/preset?v=1&n=test&s=invalid-json"))
    assertNull(repository.readFromUrl("https://trynoice.com/preset?v=invalid&n=test&s=${Uri.encode("{}")}"))
    assertNull(repository.readFromUrl("https://trynoice.com/preset?n=test&ps=invalid-json"))
    assertNull(repository.readFromUrl("https://trynoice.com/preset?n=test"))
  }

  @Test
  fun writeAsUrl() = runTest {
    val index = presetDtos.indices.random()
    val dto = presetDtos[index]
    val urlV1 = "https://trynoice.com/preset?v=1&" +
      "n=${Uri.encode(dto.name)}&" +
      "s=${Uri.encode(dto.soundStatesJson)}"

    val repository = presetRepository(this)
    assertEquals(urlV1, repository.writeAsUrl(presets[index]))
  }

  @Test
  fun defaultPresetMigration_onFirstLaunch() = runTest {
    coEvery { presetDaoMock.getDefaultPresetsSyncedVersion() } returns null
    presetRepository(this) // creating the instance will do the migration
    listOf(
      "808feaed-f4ce-4d1e-9179-ae7aec31180e",
      "13006e01-9413-45d7-bffc-dc577b077d67",
      "b76ac285-1265-472c-bcdc-aecba3a28fa2",
      "b6eb323d-e146-4690-a1a8-b8d6802001b3",
    ).forEach { presetId ->
      coVerify(exactly = 1) { presetDaoMock.save(withArg { assertEquals(presetId, it.id) }) }
    }
  }

  @Test
  fun defaultPresetMigration_onAddingNewDefaultPresets() = runTest {
    coEvery { presetDaoMock.getDefaultPresetsSyncedVersion() } returns 0
    presetRepository(this) // creating the instance will do the migration
    mapOf(
      "808feaed-f4ce-4d1e-9179-ae7aec31180e" to 0,
      "13006e01-9413-45d7-bffc-dc577b077d67" to 0,
      "b76ac285-1265-472c-bcdc-aecba3a28fa2" to 0,
      "b6eb323d-e146-4690-a1a8-b8d6802001b3" to 1,
    ).forEach { (presetId, callCount) ->
      coVerify(exactly = callCount) {
        presetDaoMock.save(withArg { assertEquals(presetId, it.id) })
      }
    }
  }

  @Test
  fun defaultPresetMigration_onRepeatedLaunch() = runTest {
    var presetsSyncVersion: Int? = 1
    coEvery { presetDaoMock.getDefaultPresetsSyncedVersion() } answers { presetsSyncVersion }
    // migration procedures update the default preset sync version.
    coEvery { presetDaoMock.saveDefaultPresetsSyncVersion(any()) } answers {
      presetsSyncVersion = firstArg<DefaultPresetsSyncVersionDto>().version
    }

    presetRepository(this) // creating the instance will do the migration
    mapOf(
      "808feaed-f4ce-4d1e-9179-ae7aec31180e" to 0,
      "13006e01-9413-45d7-bffc-dc577b077d67" to 0,
      "b76ac285-1265-472c-bcdc-aecba3a28fa2" to 0,
      "b6eb323d-e146-4690-a1a8-b8d6802001b3" to 0,
    ).forEach { (presetId, callCount) ->
      coVerify(exactly = callCount) {
        presetDaoMock.save(withArg { assertEquals(presetId, it.id) })
      }
    }
  }

  private fun presetRepository(scope: TestScope): PresetRepository {
    val dispatcher = StandardTestDispatcher(scope.testScheduler)
    val repository = PresetRepository(
      context = ApplicationProvider.getApplicationContext(),
      appDb = mockk {
        every { presets() } returns presetDaoMock
        val transactionLambda = slot<suspend () -> Unit>()
        coEvery { withTransaction(capture(transactionLambda)) } coAnswers { transactionLambda.captured.invoke() }
      },
      soundRepository = soundRepositoryMock,
      gson = gson,
      appScope = scope,
      appDispatchers = AppDispatchers(main = dispatcher, io = dispatcher),
    )

    scope.advanceUntilIdle() // run migrations
    return repository
  }
}

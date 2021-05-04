package com.github.ashutoshgngwr.noice.repository

import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.model.Sound
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectionLookupType
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.skyscreamer.jsonassert.JSONAssert
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*

@RunWith(RobolectricTestRunner::class)
class PresetRepositoryTest {

  private lateinit var prefs: SharedPreferences
  private lateinit var prefsEditor: SharedPreferences.Editor

  @OverrideMockKs(InjectionLookupType.BY_NAME)
  private lateinit var repository: PresetRepository

  @Before
  fun setup() {
    prefsEditor = mockk {
      every { putString(PresetRepository.PREFERENCE_KEY, any()) } returns this
      every { commit() } returns true
    }

    prefs = mockk {
      every { edit() } returns prefsEditor
    }

    repository = PresetRepository.newInstance(ApplicationProvider.getApplicationContext())
    MockKAnnotations.init(this)
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testCreate() {
    val preset1 = """{
      "id": "test-id-1",
      "name": "test-1",
      "playerStates": [{
        "soundKey": "test-1",
        "volume": 17,
        "timePeriod": 45
      }]
    }""".trimIndent()

    val preset2 = """{
      "id": "test-id-2",
      "name": "test-2",
      "playerStates": [{
        "soundKey": "test-1",
        "volume": 17,
        "timePeriod": 45
      }]
    }""".trimIndent()

    every { prefs.getString(PresetRepository.PREFERENCE_KEY, any()) } returns "[$preset1]"
    repository.create(mockk {
      every { id } returns "test-id-2"
      every { name } returns "test-2"
      every { playerStates } returns arrayOf(mockk {
        every { soundKey } returns "test-1"
        every { volume } returns 17
        every { timePeriod } returns 45
      })
    })

    val jsonSlot = slot<String>()
    verify(exactly = 1) {
      prefsEditor.putString(PresetRepository.PREFERENCE_KEY, capture(jsonSlot))
    }

    JSONAssert.assertEquals("[$preset1, $preset2]", jsonSlot.captured, true)
  }

  @Test
  fun testList() {
    every { prefs.getString(PresetRepository.PREFERENCE_KEY, any()) } returns """
        [{
          "id": "test-id-1",
          "name": "test-1",
          "playerStates": [{
            "soundKey": "test-1",
            "timePeriod": 60,
            "volume": 15
          }]
        }]
      """

    val presets = repository.list()
    assertEquals(1, presets.size)
    assertEquals("test-id-1", presets[0].id)
    assertEquals("test-1", presets[0].name)
    assertEquals(1, presets[0].playerStates.size)
    assertEquals(presets[0].playerStates[0], Preset.PlayerState("test-1", 15, 60))
  }

  @Test
  fun testGet() {
    every { prefs.getString(PresetRepository.PREFERENCE_KEY, any()) } returns """
      [{
        "id": "test-id-1",
        "name": "test-1",
        "playerStates": [{
          "soundKey": "test-1",
          "timePeriod": 30,
          "volume": 15
        }]
      },
      {
        "id": "test-id-2",
        "name": "test-2",
        "playerStates": [{
          "soundKey": "test-1",
          "timePeriod": 30,
          "volume": 15
        }]
      }]
    """

    assertNull(repository.get("test-id-0"))
    assertNotNull(repository.get("test-id-1"))
    assertNotNull(repository.get("test-id-2"))
  }

  @Test
  fun testRandom() {
    for (tag in arrayOf(null, Sound.Tag.FOCUS, Sound.Tag.RELAX)) {
      for (intensity in arrayOf(2 until 5, 6 until 9, 1 until 3)) {
        val preset = repository.random(tag, intensity)
        assertTrue(
          "should have expected intensity",
          preset.playerStates.size in intensity
        )

        preset.playerStates.forEach {
          assertTrue(
            "should have expected sound tags",
            tag == null || Sound.get(it.soundKey).tags.contains(tag)
          )
        }
      }
    }
  }

  @Test
  fun testUpdate() {
    every { prefs.getString(PresetRepository.PREFERENCE_KEY, any()) } returns """[{
      "id": "test-id-1",
      "name": "test-1",
      "playerStates": [{
        "soundKey": "test-1",
        "timePeriod": 30,
        "volume": 15
      }]
    }]""".trimIndent()

    repository.update(mockk {
      every { id } returns "test-id-1"
      every { name } returns "updated-name"
      every { playerStates } returns emptyArray()
    })

    val jsonSlot = slot<String>()
    verify(exactly = 1) {
      prefsEditor.putString(PresetRepository.PREFERENCE_KEY, capture(jsonSlot))
    }

    val expectedJSON = """[{
      "id": "test-id-1",
      "name": "updated-name",
      "playerStates": []
    }]""".trimIndent()

    JSONAssert.assertEquals(expectedJSON, jsonSlot.captured, true)
  }

  @Test
  fun testDelete() {
    val preset1 = """{
      "id": "test-id-1",
      "name": "test-1",
      "playerStates": [{
        "soundKey": "test-1",
        "timePeriod": 30,
        "volume": 15
      }]
    }""".trimIndent()

    val preset2 = """{
      "id": "test-id-2",
      "name": "test-2",
      "playerStates": [{
        "soundKey": "test-1",
        "timePeriod": 30,
        "volume": 15
      }]
    }""".trimIndent()

    every {
      prefs.getString(PresetRepository.PREFERENCE_KEY, any())
    } returns "[$preset1, $preset2]"

    repository.delete("test-id-1")

    val jsonSlot = slot<String>()
    verify(exactly = 1) {
      prefsEditor.putString(PresetRepository.PREFERENCE_KEY, capture(jsonSlot))
    }

    JSONAssert.assertEquals("[$preset2]", jsonSlot.captured, true)
  }

  @Test
  fun testExportTo() {
    val presetData = "test-data"
    every { prefs.getString(PresetRepository.PREFERENCE_KEY, any()) } returns presetData
    val stream = ByteArrayOutputStream()
    repository.exportTo(stream)

    val expectedOutput = """{
      "${PresetRepository.EXPORT_VERSION_KEY}": "${PresetRepository.PREFERENCE_KEY}",
      "${PresetRepository.EXPORT_DATA_KEY}": "$presetData"
    }""".trimIndent()
    JSONAssert.assertEquals(expectedOutput, stream.toString(), true)
  }

  @Test
  fun importFrom() {
    val presetData = "test-data"
    val input = """{
      "${PresetRepository.EXPORT_VERSION_KEY}": "${PresetRepository.PREFERENCE_KEY}",
      "${PresetRepository.EXPORT_DATA_KEY}": "$presetData"
    }"""

    every { prefs.getString(any(), any()) } returns null
    repository.importFrom(ByteArrayInputStream(input.toByteArray()))
    verify(exactly = 1) { prefsEditor.putString(PresetRepository.PREFERENCE_KEY, presetData) }
  }

  @Test
  fun testMigrateToV1() {
    every { prefsEditor.remove(PresetRepository.PREF_V0) } returns prefsEditor
    every { prefs.getString(PresetRepository.PREF_V0, any()) } returns """
        [{
          "a": "test",
          "b": [{
            "a": "test-1",
            "c": 30,
            "b": 0.75
          }]
        }]
      """

    // mock UUID classes since id will get generated on migration
    val mockUUID = mockk<UUID>(relaxed = true)
    every { mockUUID.toString() } returns "test-id"
    mockkStatic(UUID::class)
    every { UUID.randomUUID() } returns mockUUID

    val expectedV1JSON = """
      [{
        "id": "test-id",
        "name": "test",
        "playerStates": [{
          "soundKey": "test-1",
          "timePeriod": 60,
          "volume": 15
        }]
      }]
    """

    repository.migrateToV1()

    val jsonSlot = slot<String>()
    verify(exactly = 1) {
      prefsEditor.remove(PresetRepository.PREF_V0)
      prefsEditor.putString(PresetRepository.PREF_V1, capture(jsonSlot))
    }

    JSONAssert.assertEquals(expectedV1JSON, jsonSlot.captured, true)
  }
}

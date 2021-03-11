package com.github.ashutoshgngwr.noice.sound

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.skyscreamer.jsonassert.JSONAssert
import java.util.*

@RunWith(RobolectricTestRunner::class)
class PresetTest {

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testEquality() {
    val preset1 = Preset.from("test-0", listOf(
      mockk(relaxed = true) {
        every { soundKey } returns "test"
        every { volume } returns 15
        every { timePeriod } returns 30
      }
    ))

    val preset2 = Preset.from("test-1", listOf(
      mockk(relaxed = true) {
        every { soundKey } returns "test-x"
        every { volume } returns 15
        every { timePeriod } returns 30
      }
    ))

    val preset3 = Preset.from("test-2", listOf(
      mockk(relaxed = true) {
        every { soundKey } returns "test"
        every { volume } returns 20
        every { timePeriod } returns 30
      }
    ))

    val preset4 = Preset.from("test-2", listOf(
      mockk(relaxed = true) {
        every { soundKey } returns "test"
        every { volume } returns 15
        every { timePeriod } returns 40
      }
    ))

    val preset5 = Preset.from("test-2", listOf(
      mockk(relaxed = true) {
        every { soundKey } returns "test"
        every { volume } returns 15
        every { timePeriod } returns 30
      }
    ))

    assertNotEquals(preset1, preset2)
    assertNotEquals(preset1, preset3)
    assertNotEquals(preset1, preset4)
    assertEquals(preset1, preset5)
  }

  @Test
  fun testReadAllFromUserPreferences() {
    mockkStatic(PreferenceManager::class)
    val mockPrefs = mockk<SharedPreferences>(relaxed = true) {
      every { getString(Preset.PREFERENCE_KEY, any()) } returns """
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
    }

    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs

    val presets = Preset.readAllFromUserPreferences(mockk())
    assertEquals(1, presets.size)
    assertEquals("test-id-1", presets[0].id)
    assertEquals("test-1", presets[0].name)
    assertEquals(1, presets[0].playerStates.size)
    assertEquals(
      presets[0].playerStates[0], Preset.PlayerState("test-1", 15, 60)
    )
  }

  @Test
  fun testWriteAllToUserPreferences() {
    mockkStatic(PreferenceManager::class)
    val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true) {
      every { putString(any(), any()) } returns this
    }

    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockk(relaxed = true) {
      every { edit() } returns mockPrefsEditor
    }

    // mock UUID classes since id gets generated on write if not already present
    mockkStatic(UUID::class)
    val mockUUID = mockk<UUID>(relaxed = true)
    every { mockUUID.toString() } returns "test-id-1"
    every { UUID.randomUUID() } returns mockUUID

    val expectedJSON = """
      [{
        "id": "test-id-1",
        "name": "test-1",
        "playerStates": [{
          "soundKey": "test-1",
          "volume": 17,
          "timePeriod": 45
        }]
      },
      {
        "id": "test-id-2",
        "name": "test-2",
        "playerStates": [{
          "soundKey": "test-1",
          "volume": 17,
          "timePeriod": 45
        }]
      }]
    """

    val mockPlayerState = mockk<Preset.PlayerState> {
      every { soundKey } returns "test-1"
      every { volume } returns 17
      every { timePeriod } returns 45
    }

    Preset.writeAllToUserPreferences(mockk(), listOf(
      mockk(relaxed = true) {
        every { id } returns ""
        every { name } returns "test-1"
        every { playerStates } returns arrayOf(mockPlayerState)
      },
      mockk(relaxed = true) {
        every { id } returns "test-id-2"
        every { name } returns "test-2"
        every { playerStates } returns arrayOf(mockPlayerState)
      }
    ))

    val stringSlot = slot<String>()
    verify(exactly = 1) {
      mockPrefsEditor.putString(Preset.PREFERENCE_KEY, capture(stringSlot))
      mockPrefsEditor.apply()
    }

    JSONAssert.assertEquals(expectedJSON, stringSlot.captured, true)
  }

  @Test
  fun testAppendToUserPreferences() {
    mockkStatic(PreferenceManager::class)
    val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true) {
      every { putString(any(), any()) } returns this
    }

    val mockPrefs = mockk<SharedPreferences>(relaxed = true) {
      every { edit() } returns mockPrefsEditor
    }

    val presetJSON1 = """
      {
        "id": "test-id-1",
        "name": "test-1",
        "playerStates": [{
          "soundKey": "test-1",
          "timePeriod": 100,
          "volume": 13
        }]
      }
    """

    val presetJSON2 = """
      {
        "id": "test-id-2",
        "name": "test-2",
        "playerStates": [{
          "soundKey": "test-1",
          "timePeriod": 79,
          "volume": 14
        }]
      }
    """

    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
    every { mockPrefs.getString(Preset.PREFERENCE_KEY, any()) } returns "[${presetJSON1}]"

    Preset.appendToUserPreferences(mockk(), mockk(relaxed = true) {
      every { id } returns "test-id-2"
      every { name } returns "test-2"
      every { playerStates } returns arrayOf(mockk {
        every { soundKey } returns "test-1"
        every { volume } returns 14
        every { timePeriod } returns 79
      })
    })

    val stringSlot = slot<String>()
    verify(exactly = 1) {
      mockPrefsEditor.putString(Preset.PREFERENCE_KEY, capture(stringSlot))
      mockPrefsEditor.apply()
    }

    JSONAssert.assertEquals("[${presetJSON1}, ${presetJSON2}]", stringSlot.captured, true)
  }

  @Test
  fun testDuplicateNameValidator() {
    mockkStatic(PreferenceManager::class)
    val mockPrefs = mockk<SharedPreferences>(relaxed = true)
    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
    every { mockPrefs.getString(Preset.PREFERENCE_KEY, any()) } returns """
      [{
        "id": "test-id",
        "name": "test",
        "playerStates": [{
          "soundKey": "test-1",
          "timePeriod": 30,
          "volume": 15
        }]
      }]
    """

    val validator = Preset.duplicateNameValidator(mockk())
    assertTrue(validator.invoke("test"))
    assertFalse(validator.invoke("test-invalid"))
  }

  @Test
  fun testFindByID() {
    mockkStatic(PreferenceManager::class)
    val mockPrefs = mockk<SharedPreferences>(relaxed = true)
    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
    every { mockPrefs.getString(Preset.PREFERENCE_KEY, any()) } returns """
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

    assertNull(Preset.findByID(mockk(), "test-id-0"))
    assertNotNull(Preset.findByID(mockk(), "test-id-1"))
    assertNotNull(Preset.findByID(mockk(), "test-id-2"))
  }

  @Test
  fun testMigrateToV1() {
    mockkStatic(PreferenceManager::class)
    val mockPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true) {
      every { putString(any(), any()) } returns this
    }

    val mockPrefs = mockk<SharedPreferences>(relaxed = true) {
      every { edit() } returns mockPrefsEditor
      every { getString(Preset.PREF_V0, any()) } returns """
        [{
          "a": "test",
          "b": [{
            "a": "test-1",
            "c": 30,
            "b": 0.75
          }]
        }]
      """
    }

    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs

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

    Preset.migrateAllToV1(mockk())

    val jsonSlot = slot<String>()
    verify(exactly = 1) {
      mockPrefsEditor.remove(Preset.PREF_V0)
      mockPrefsEditor.putString(Preset.PREF_V1, capture(jsonSlot))
    }

    JSONAssert.assertEquals(expectedV1JSON, jsonSlot.captured, true)
  }

  @Test
  fun testGenerateRandom() {
    for (tag in arrayOf(null, Sound.Tag.FOCUS, Sound.Tag.RELAX)) {
      for (intensity in arrayOf(2 until 5, 6 until 9, 1 until 3)) {
        val preset = Preset.generateRandom(tag, intensity)
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
}

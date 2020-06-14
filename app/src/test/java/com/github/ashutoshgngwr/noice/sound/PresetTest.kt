package com.github.ashutoshgngwr.noice.sound

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.sound.player.Player
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.skyscreamer.jsonassert.JSONAssert

@RunWith(RobolectricTestRunner::class)
class PresetTest {

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
    val mockPrefs = mockk<SharedPreferences>(relaxed = true)
    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
    every { mockPrefs.getString("presets", any()) } returns "[{" +
      "  \"a\":\"test\"," +
      "  \"b\":[" +
      "    {" +
      "      \"a\":\"test-1\"," +
      "      \"c\":30," +
      "      \"b\":0.75" +
      "    }" +
      "  ]" +
      "}]"

    val presets = Preset.readAllFromUserPreferences(mockk())
    assertEquals(1, presets.size)
    assertEquals("test", presets[0].name)
    assertEquals(1, presets[0].playerStates.size)
    assertEquals("test-1", presets[0].playerStates[0].soundKey)
    // returned time period should have correct offset
    assertEquals(Player.MIN_TIME_PERIOD + 30, presets[0].playerStates[0].timePeriod)
    assertEquals(15, presets[0].playerStates[0].volume)
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

    val expectedJSON = "[{" +
      "  \"a\":\"test\"," +
      "  \"b\":[" +
      "    {" +
      "      \"a\":\"test-1\"," +
      "      \"c\":30," +
      "      \"b\":0.75" +
      "    }" +
      "  ]" +
      "}]"

    Preset.writeAllToUserPreferences(mockk(), listOf(
      mockk(relaxed = true) {
        every { name } returns "test"
        every { playerStates } returns arrayOf(mockk {
          every { soundKey } returns "test-1"
          every { volume } returns 15
          every { timePeriod } returns Player.MIN_TIME_PERIOD + 30
        })
      }
    ))

    val stringSlot = slot<String>()
    verify(exactly = 1) {
      mockPrefsEditor.putString("presets", capture(stringSlot))
      mockPrefsEditor.apply()
    }

    JSONAssert.assertEquals(expectedJSON, stringSlot.captured, false)
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

    val presetJSON1 = "{" +
      "  \"a\":\"test-1\"," +
      "  \"b\":[" +
      "    {" +
      "      \"a\":\"test-1\"," +
      "      \"c\":30," +
      "      \"b\":0.75" +
      "    }" +
      "  ]" +
      "}"

    val presetJSON2 = "{" +
      "  \"a\":\"test-2\"," +
      "  \"b\":[" +
      "    {" +
      "      \"a\":\"test-1\"," +
      "      \"c\":30," +
      "      \"b\":0.75" +
      "    }" +
      "  ]" +
      "}"

    every { PreferenceManager.getDefaultSharedPreferences(any()) } returns mockPrefs
    every { mockPrefs.getString("presets", any()) } returns "[${presetJSON1}]"

    Preset.appendToUserPreferences(mockk(), mockk(relaxed = true) {
      every { name } returns "test-2"
      every { playerStates } returns arrayOf(mockk {
        every { soundKey } returns "test-1"
        every { volume } returns 15
        every { timePeriod } returns Player.MIN_TIME_PERIOD + 30
      })
    })

    val stringSlot = slot<String>()
    verify(exactly = 1) {
      mockPrefsEditor.putString("presets", capture(stringSlot))
      mockPrefsEditor.apply()
    }

    JSONAssert.assertEquals("[${presetJSON1}, ${presetJSON2}]", stringSlot.captured, false)
  }
}

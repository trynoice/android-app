package com.github.ashutoshgngwr.noice.model

import android.net.Uri
import io.mockk.every
import io.mockk.mockk
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
  fun testFromUri() {
    val presetUri = "noice://preset?name=test&playerStates=%5B%7B%22soundKey%22%3A%22test-1%22%2C" +
      "%22timePeriod%22%3A300%2C%22volume%22%3A4%7D%2C%7B%22soundKey%22%3A%22test-2%22%2C%22timeP" +
      "eriod%22%3A300%2C%22volume%22%3A4%7D%5D"

    val expectedOutput = arrayOf(
      Preset.PlayerState("test-1", 4, 300),
      Preset.PlayerState("test-2", 4, 300)
    )

    val preset = Preset.from(Uri.parse(presetUri))
    assertEquals("test", preset.name)
    assertEquals(expectedOutput.size, preset.playerStates.size)
    for (i in expectedOutput.indices) {
      assertEquals(expectedOutput[i], preset.playerStates[i])
    }
  }

  @Test
  fun testToUri() {
    val preset = Preset.from("test", listOf(
      mockk(relaxed = true) {
        every { soundKey } returns "test"
        every { volume } returns 15
        every { timePeriod } returns 30
      }
    ))

    val uri = preset.toUri()
    assertEquals(preset.name, uri.getQueryParameter(Preset.URI_NAME_PARAM))

    val playerStatesJSON = """[{
      "soundKey": "test",
      "volume": 15,
      "timePeriod": 30
    }]""".trimIndent()

    JSONAssert.assertEquals(
      playerStatesJSON,
      uri.getQueryParameter(Preset.URI_PLAYER_STATES_PARAM),
      true
    )
  }
}

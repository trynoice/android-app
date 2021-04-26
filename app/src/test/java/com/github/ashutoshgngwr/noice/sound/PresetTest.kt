package com.github.ashutoshgngwr.noice.sound

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
  fun testGenerateRandom() {
    for (tag in arrayOf(null, Sound.Tag.FOCUS, Sound.Tag.RELAX)) {
      for (intensity in arrayOf(2 until 5, 6 until 9, 1 until 3)) {
        val preset = Preset.random(tag, intensity)
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

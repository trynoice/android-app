package com.github.ashutoshgngwr.noice.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SoundTest {

  @Test
  fun testSoundSources() {
    // this is to validate whether the source files provided in the sound library exist.
    // main goal here is to avoid typos in one of the sound samples since it's hard to notice
    // if all samples of a sound are playing when played simultaneously.
    val files = ApplicationProvider.getApplicationContext<Context>().assets.list("")
    requireNotNull(files).toSet()

    Sound.LIBRARY.forEach { (_, sound) ->
      sound.src.forEach {
        assertTrue("'$it' not found in assets", files.contains(it))
      }
    }
  }

  @Test
  fun testFilterLibraryByTag() {
    assertEquals(
      "should contain all keys when tag is null",
      Sound.LIBRARY.size,
      Sound.filterLibraryByTag(null).size
    )

    for (tag in Sound.Tag.values()) {
      Sound.filterLibraryByTag(tag).forEach {
        assertTrue(
          "should only contain keys of sounds that have the given tag",
          Sound.get(it).tags.contains(tag)
        )
      }
    }
  }
}

package com.github.ashutoshgngwr.noice

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.sound.Preset
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class ShortcutHandlerActivityTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testOnCreate() {
    val presetIDExpectations = arrayOf("invalid-id", "valid-id")
    val presetFindByIdReturns = arrayOf(null, mockk<Preset>(relaxed = true))
    val nPresetPlayerStateCalls = arrayOf(0, 1)

    mockkObject(Preset.Companion)
    for (i in presetIDExpectations.indices) {
      every { Preset.findByID(any(), presetIDExpectations[i]) } returns presetFindByIdReturns[i]

      Intents.init()
      Intents.intending(hasComponent(MainActivity::class.qualifiedName))
        .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent()))

      try {
        Intent(ApplicationProvider.getApplicationContext(), ShortcutHandlerActivity::class.java)
          .putExtra(ShortcutHandlerActivity.EXTRA_PRESET_ID, presetIDExpectations[i])
          .also { ActivityScenario.launch<ShortcutHandlerActivity>(it).close() }

        Intents.intended(
          allOf(
            hasComponent(MainActivity::class.qualifiedName),
            hasExtra(MainActivity.EXTRA_CURRENT_NAVIGATED_FRAGMENT, R.id.saved_presets)
          ),
          Intents.times(1)
        )

        // since there is no way to match the service intents, we'll have to rely on the preset mock.
        // if the media player service was started with an intent to play a preset, we can verify
        // the calls on the preset mock.
        // best source I could find: https://stackoverflow.com/a/32131576/2410641
        presetFindByIdReturns[i]?.also {
          verify(atLeast = nPresetPlayerStateCalls[i]) { it.playerStates }
        }

      } finally {
        Intents.release()
        clearMocks(Preset.Companion)
      }
    }
  }
}

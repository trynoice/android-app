package com.github.ashutoshgngwr.noice.activity

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.engine.PlaybackController
import com.github.ashutoshgngwr.noice.model.Preset
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class PresetShortcutHandlerActivityTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @BindValue
  internal lateinit var mockPlaybackController: PlaybackController

  @BindValue
  internal lateinit var mockPresetRepository: PresetRepository

  @Before
  fun setup() {
    mockPlaybackController = mockk(relaxed = true)
    mockPresetRepository = mockk(relaxed = true)
  }

  @Test
  fun onCreate() {
    val presetIDExpectations = arrayOf("invalid-id", "valid-id")
    val presetFindByIdReturns = arrayOf(null, mockk<Preset>(relaxed = true))
    val playPresetCallCount = arrayOf(0, 1)
    val context = ApplicationProvider.getApplicationContext<Context>()

    for (i in presetIDExpectations.indices) {
      every { mockPresetRepository.get(presetIDExpectations[i]) } returns presetFindByIdReturns[i]
      Intents.init()
      Intents.intending(hasComponent(MainActivity::class.qualifiedName))
        .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent()))

      try {
        Intent(context, PresetShortcutHandlerActivity::class.java)
          .putExtra(PresetShortcutHandlerActivity.EXTRA_SHORTCUT_ID, presetIDExpectations[i])
          .putExtra(PresetShortcutHandlerActivity.EXTRA_PRESET_ID, presetIDExpectations[i])
          .also { ActivityScenario.launch<PresetShortcutHandlerActivity>(it) }

        Intents.intended(
          allOf(
            hasComponent(MainActivity::class.qualifiedName),
            hasExtra(MainActivity.EXTRA_HOME_DESTINATION, R.id.presets)
          ),
          Intents.times(1)
        )

        verify(exactly = playPresetCallCount[i], timeout = 5000L) {
          mockPlaybackController.play(presetFindByIdReturns[i] ?: mockk())
        }
      } finally {
        Intents.release()
      }
    }
  }
}

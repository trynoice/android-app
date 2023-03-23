package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import androidx.fragment.app.setFragmentResultListener
import androidx.paging.PagingData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.github.ashutoshgngwr.noice.EspressoX.launchFragmentInHiltContainer
import com.github.ashutoshgngwr.noice.HiltFragmentScenario
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.ext.getSerializableCompat
import com.github.ashutoshgngwr.noice.models.Preset
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class PresetPickerFragmentTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @BindValue
  internal lateinit var presetRepositoryMock: PresetRepository

  private val presets = listOf(
    Preset(id = "preset-1", name = "preset-1", soundStates = sortedMapOf()),
    Preset(id = "preset-2", name = "preset-2", soundStates = sortedMapOf()),
    Preset(id = "preset-3", name = "preset-3", soundStates = sortedMapOf()),
    Preset(id = "preset-4", name = "preset-4", soundStates = sortedMapOf()),
    Preset(id = "preset-5", name = "preset-5", soundStates = sortedMapOf()),
  )

  @Before
  fun setUp() {
    presetRepositoryMock = mockk {
      every { pagingDataFlow(any()) } answers {
        presets.filter { it.name.contains(firstArg<String>(), true) }
          .let { PagingData.from(it) }
          .let { flowOf(it) }
      }
    }
  }

  @Test
  fun initiallySelectedPreset() {
    val selected = presets.random()
    launchPresetPickerFragment(selectedPreset = selected)
    onView(withText(selected.name))
      .check(matches(isDisplayed()))
      .check(matches(isChecked()))

    presets
      .filterNot { it == selected }
      .forEach { preset ->
        onView(withText(preset.name))
          .check(matches(isDisplayed()))
          .check(matches(isNotChecked()))
      }
  }

  @Test
  fun searchPresets() {
    launchPresetPickerFragment()
    onView(withHint(R.string.search_preset))
      .check(matches(isDisplayed()))
      .perform(typeText("1\n"))

    onView(withText("preset-1"))
      .check(matches(isDisplayed()))

    presets
      .filterNot { it.name == "preset-1" }
      .forEach { preset ->
        onView(withText(preset.name))
          .check(doesNotExist())
      }
  }

  @Test
  fun presetSelection() {
    val resultKey = "test-preset-picker-result"
    val listener = mockk<(String, Bundle) -> Unit>(relaxed = true)
    launchPresetPickerFragment(resultKey = resultKey)
      .onFragment { it.setFragmentResultListener(resultKey, listener) }
      .use {
        val selected = presets.random()
        onView(withText(selected.name))
          .check(matches(isDisplayed()))
          .perform(click())

        verify(exactly = 1, timeout = 5000L) {
          listener.invoke(eq(resultKey), withArg { result ->
            result.getSerializableCompat(PresetPickerFragment.EXTRA_SELECTED_PRESET, Preset::class)
              .also { assertEquals(selected, it) }
          })
        }
      }

    clearMocks(listener)
    launchPresetPickerFragment(resultKey = resultKey)
      .onFragment { it.setFragmentResultListener(resultKey, listener) }
      .use {
        onView(withText(R.string.random_preset))
          .check(matches(isDisplayed()))
          .perform(click())

        verify(exactly = 1, timeout = 5000L) {
          listener.invoke(eq(resultKey), withArg { result ->
            result.getSerializableCompat(PresetPickerFragment.EXTRA_SELECTED_PRESET, Preset::class)
              .also { assertNull(it) }
          })
        }
      }
  }

  private fun launchPresetPickerFragment(
    resultKey: String = "test-result-key",
    selectedPreset: Preset? = null,
  ): HiltFragmentScenario<PresetPickerFragment> {
    return PresetPickerFragmentArgs(fragmentResultKey = resultKey, selectedPreset = selectedPreset)
      .toBundle()
      .let { launchFragmentInHiltContainer(fragmentArgs = it) }
  }
}

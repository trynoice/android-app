package com.github.ashutoshgngwr.noice.fragment

import android.view.View
import androidx.fragment.app.Fragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.EspressoX.launchFragmentInHiltContainer
import com.github.ashutoshgngwr.noice.HiltFragmentScenario
import com.github.ashutoshgngwr.noice.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

@HiltAndroidTest
class DialogFragmentTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  private lateinit var emptyFragmentScenario: HiltFragmentScenario<Fragment>

  @Before
  fun setup() {
    emptyFragmentScenario = launchFragmentInHiltContainer()
  }

  @Test
  fun testTitleText() {
    newDialog { title(R.string.app_name) }
    onView(allOf(withId(R.id.title), withText(R.string.app_name)))
      .inRoot(isDialog())
      .check(matches(isDisplayed()))
  }

  @Test
  fun testPositiveButton() {
    newDialog { positiveButton(R.string.app_name) }
    onView(allOf(withId(R.id.positive), withText(R.string.app_name)))
      .inRoot(isDialog())
      .perform(click())

    onView(isRoot()).inRoot(not(isDialog()))
  }

  @Test
  fun testNegativeButton() {
    newDialog { negativeButton(R.string.app_name) }
    onView(allOf(withId(R.id.negative), withText(R.string.app_name)))
      .inRoot(isDialog())
      .perform(click())

    onView(isRoot()).inRoot(not(isDialog()))
  }

  @Test
  fun testNeutralButton() {
    newDialog { neutralButton(android.R.string.copy) }
    onView(allOf(withId(R.id.neutral), withText(android.R.string.copy)))
      .inRoot(isDialog())
      .perform(click())

    onView(isRoot()).inRoot(not(isDialog()))
  }

  @Test
  fun testMessageText() {
    newDialog { message(R.string.app_name) }
    onView(allOf(isDescendantOfA(withId(R.id.content)), withText(R.string.app_name)))
      .inRoot(isDialog())
      .check(matches(isDisplayed()))
  }

  @Test
  fun testTextInput() {
    val mockValidator = mockk<(String) -> Int>()
    every { mockValidator.invoke("invalid") } returns R.string.app_name
    every { mockValidator.invoke("test") } returns 0
    lateinit var textGetter: InputTextGetter
    newDialog {
      textGetter = input(
        hintRes = R.string.app_name,
        preFillValue = "test",
        validator = mockValidator
      )
    }

    onView(allOf(isDescendantOfA(withId(R.id.content)), withId(R.id.editText)))
      .inRoot(isDialog())
      .check(matches(isDisplayed()))
      .perform(replaceText("invalid"))

    onView(withId(R.id.textInputLayout))
      .inRoot(isDialog())
      .check(matches(EspressoX.withErrorText(R.string.app_name)))

    onView(withId(R.id.positive))
      .inRoot(isDialog())
      .check(matches(not(isEnabled())))

    onView(allOf(isDescendantOfA(withId(R.id.content)), withId(R.id.editText)))
      .inRoot(isDialog())
      .perform(replaceText("test"))

    onView(withId(R.id.textInputLayout))
      .inRoot(isDialog())
      .check(matches(not(EspressoX.withErrorText(R.string.app_name))))

    assertEquals("test", textGetter.invoke())
  }

  @Test
  fun testSingleChoiceList() {
    var selectedItem = 0
    val items = arrayOf("test-0", "test-1", "test-2")

    newDialog {
      singleChoiceItems(items, currentChoice = selectedItem) { choice ->
        selectedItem = choice
      }
    }

    items.forEach {
      onView(allOf(isDescendantOfA(withId(android.R.id.list)), withText(it)))
        .inRoot(isDialog())
        .check(matches(isDisplayed()))
    }

    onView(allOf(isDescendantOfA(withId(android.R.id.list)), withText(items[1])))
      .inRoot(isDialog())
      .perform(click())

    onView(isRoot()).inRoot(not(isDialog()))
    assertEquals(1, selectedItem)
  }

  @Test
  fun testSlider() {
    val id = View.generateViewId()
    val expectedValue = Random.nextInt(10).toFloat()
    var value = 1.0f

    newDialog {
      slider(
        viewID = id,
        from = 0.0f,
        to = 10.0f,
        value = value,
        changeListener = { v -> value = v },
      )

      positiveButton(R.string.okay)
    }

    onView(withId(id))
      .inRoot(isDialog())
      .check(matches(isDisplayed()))
      .perform(EspressoX.slide(expectedValue))

    onView(withId(R.id.positive))
      .inRoot(isDialog())
      .perform(click())

    assertEquals(expectedValue, value)
  }

  private fun newDialog(options: DialogFragment.() -> Unit) {
    emptyFragmentScenario.onFragment {
      DialogFragment.show(it.childFragmentManager, options)
    }

    // wait for dialog to be visible
    onView(withId(R.id.dialog_root))
      .inRoot(isDialog())
      .check(matches(isDisplayed()))
  }
}

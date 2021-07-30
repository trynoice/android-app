package com.github.ashutoshgngwr.noice.fragment

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.RetryTestRule
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random


@RunWith(AndroidJUnit4::class)
class DialogFragmentTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  private lateinit var emptyFragmentScenario: FragmentScenario<Fragment>

  @Before
  fun setup() {
    emptyFragmentScenario = launchFragmentInContainer(null, R.style.Theme_App)
  }

  @Test
  fun testTitleText() {
    emptyFragmentScenario.onFragment {
      DialogFragment.show(it.childFragmentManager) {
        title(R.string.app_name)
      }
    }

    onView(allOf(withId(R.id.title), withText(R.string.app_name)))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testPositiveButton() {
    emptyFragmentScenario.onFragment {
      DialogFragment.show(it.childFragmentManager) {
        positiveButton(R.string.app_name)
      }
    }

    onView(allOf(withId(R.id.positive), withText(R.string.app_name)))
      .perform(click())

    onView(withId(R.id.positive)).check(doesNotExist())
  }

  @Test
  fun testNegativeButton() {
    emptyFragmentScenario.onFragment {
      DialogFragment.show(it.childFragmentManager) {
        negativeButton(R.string.app_name)
      }
    }

    onView(allOf(withId(R.id.negative), withText(R.string.app_name)))
      .perform(click())

    onView(withId(R.id.negative)).check(doesNotExist())
  }

  @Test
  fun neutralButton() {
    emptyFragmentScenario.onFragment {
      DialogFragment.show(it.childFragmentManager) {
        neutralButton(android.R.string.copy)
      }
    }

    onView(allOf(withId(R.id.neutral), withText(android.R.string.copy)))
      .perform(click())

    onView(withId(R.id.neutral)).check(doesNotExist())
  }

  @Test
  fun testMessageText() {
    emptyFragmentScenario.onFragment {
      DialogFragment.show(it.childFragmentManager) {
        message(R.string.app_name)
      }
    }

    onView(allOf(isDescendantOfA(withId(R.id.content)), withText(R.string.app_name)))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testTextInput() {
    val mockValidator = mockk<(String) -> Int>()
    every { mockValidator.invoke("invalid") } returns R.string.app_name
    every { mockValidator.invoke("test") } returns 0
    var dialogFragment: DialogFragment? = null
    emptyFragmentScenario.onFragment {
      dialogFragment = DialogFragment.show(it.childFragmentManager) {
        input(
          hintRes = R.string.app_name,
          preFillValue = "test",
          validator = mockValidator
        )
      }
    }

    onView(allOf(isDescendantOfA(withId(R.id.content)), withId(R.id.editText)))
      .check(matches(isDisplayed()))
      .perform(replaceText("invalid"))

    onView(withId(R.id.textInputLayout))
      .check(matches(EspressoX.withErrorText(R.string.app_name)))

    onView(withId(R.id.positive))
      .check(matches(not(isEnabled())))

    onView(allOf(isDescendantOfA(withId(R.id.content)), withId(R.id.editText)))
      .perform(replaceText("test"))

    onView(withId(R.id.textInputLayout))
      .check(matches(not(EspressoX.withErrorText(R.string.app_name))))

    assertEquals("test", dialogFragment?.getInputText())
  }

  @Test
  fun testSingleChoiceList() {
    var selectedItem = 0
    val items = arrayOf("test-0", "test-1", "test-2")

    emptyFragmentScenario.onFragment {
      DialogFragment.show(it.childFragmentManager) {
        singleChoiceItems(items, currentChoice = selectedItem) { choice ->
          selectedItem = choice
        }
      }
    }

    items.forEach {
      onView(allOf(isDescendantOfA(withId(android.R.id.list)), withText(it)))
        .check(matches(isDisplayed()))
    }

    onView(allOf(isDescendantOfA(withId(android.R.id.list)), withText(items[1])))
      .perform(click())

    onView(withId(android.R.id.list)).check(doesNotExist())
    assertEquals(1, selectedItem)
  }

  @Test
  fun testSlider() {
    val id = View.generateViewId()
    val expectedValue = Random.nextInt(10).toFloat()
    var value = 1.0f

    emptyFragmentScenario.onFragment {
      DialogFragment.show(it.childFragmentManager) {
        slider(
          viewID = id,
          from = 0.0f,
          to = 10.0f,
          value = value,
          changeListener = { v -> value = v },
        )

        positiveButton(R.string.okay)
      }
    }

    onView(withId(id))
      .check(matches(isDisplayed()))
      .perform(EspressoX.slide(expectedValue))

    onView(withId(R.id.positive)).perform(click())
    assertEquals(expectedValue, value)
  }
}

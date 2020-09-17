package com.github.ashutoshgngwr.noice.fragment

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


@RunWith(AndroidJUnit4::class)
class DialogFragmentTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  private lateinit var emptyFragmentScenario: FragmentScenario<Fragment>
  private lateinit var dialogFragment: DialogFragment

  @Before
  fun setup() {
    dialogFragment = DialogFragment()
    emptyFragmentScenario = launchFragmentInContainer(null, R.style.Theme_App)
  }

  @Test
  fun testTitleText() {
    emptyFragmentScenario.onFragment {
      dialogFragment.show(it.childFragmentManager) {
        title(android.R.string.yes)
      }
    }

    onView(allOf(withId(R.id.title), withText(android.R.string.yes)))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testPositiveButton() {
    emptyFragmentScenario.onFragment {
      dialogFragment.show(it.childFragmentManager) {
        positiveButton(android.R.string.yes)
      }
    }

    EspressoX.waitForView(withId(R.id.positive), 100, 5)
      .check(matches(withText(android.R.string.yes)))
      .perform(click())

    onView(withId(R.id.positive)).check(doesNotExist())
  }

  @Test
  fun testNegativeButton() {
    emptyFragmentScenario.onFragment {
      dialogFragment.show(it.childFragmentManager) {
        negativeButton(android.R.string.no)
      }
    }

    EspressoX.waitForView(withId(R.id.negative), 100, 5)
      .check(matches(withText(android.R.string.no)))
      .perform(click())

    onView(withId(R.id.negative)).check(doesNotExist())
  }

  @Test
  fun neutralButton() {
    emptyFragmentScenario.onFragment {
      dialogFragment.show(it.childFragmentManager) {
        neutralButton(android.R.string.copy)
      }
    }

    EspressoX.waitForView(withId(R.id.neutral), 100, 5)
      .check(matches(withText(android.R.string.copy)))
      .perform(click())

    onView(withId(R.id.neutral)).check(doesNotExist())
  }

  @Test
  fun testMessageText() {
    emptyFragmentScenario.onFragment {
      dialogFragment.show(it.childFragmentManager) {
        message(android.R.string.yes)
      }
    }

    onView(allOf(isDescendantOfA(withId(R.id.content)), withText(android.R.string.yes)))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testTextInput() {
    val mockValidator = mockk<(String) -> Int>()
    every { mockValidator.invoke("invalid") } returns android.R.string.no
    every { mockValidator.invoke("test") } returns 0
    emptyFragmentScenario.onFragment {
      dialogFragment.show(it.childFragmentManager) {
        input(
          hintRes = android.R.string.yes,
          preFillValue = "test",
          validator = mockValidator
        )
      }
    }

    onView(allOf(isDescendantOfA(withId(R.id.content)), withId(R.id.editText)))
      .check(matches(isDisplayed()))
      .perform(replaceText("invalid"))

    onView(withId(R.id.textInputLayout))
      .check(matches(EspressoX.withErrorText(android.R.string.no)))

    onView(withId(R.id.positive))
      .check(matches(not(isEnabled())))

    onView(allOf(isDescendantOfA(withId(R.id.content)), withId(R.id.editText)))
      .perform(replaceText("test"))

    onView(withId(R.id.textInputLayout))
      .check(matches(not(EspressoX.withErrorText(android.R.string.no))))

    assertEquals("test", dialogFragment.getInputText())
  }

  @Test
  fun testSingleChoiceList() {
    var selectedItem = 0
    val items = arrayOf("test-0", "test-1", "test-2")

    emptyFragmentScenario.onFragment {
      dialogFragment.show(it.childFragmentManager) {
        singleChoiceItems(items, currentChoice = selectedItem) { choice ->
          selectedItem = choice
        }
      }
    }

    items.forEach {
      EspressoX.waitForView(allOf(isDescendantOfA(withId(android.R.id.list)), withText(it)), 100, 5)
        .check(matches(isDisplayed()))
    }

    onView(allOf(isDescendantOfA(withId(android.R.id.list)), withText(items[1])))
      .perform(click())

    onView(withId(android.R.id.list)).check(doesNotExist())
    assertEquals(1, selectedItem)
  }
}

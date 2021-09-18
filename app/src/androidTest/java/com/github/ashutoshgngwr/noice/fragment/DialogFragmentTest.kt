package com.github.ashutoshgngwr.noice.fragment

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.fragment.app.testing.withFragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.R
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class DialogFragmentTest {

  private lateinit var emptyFragmentScenario: FragmentScenario<Fragment>

  @Before
  fun setup() {
    emptyFragmentScenario = launchFragmentInContainer(null, R.style.Theme_App)
  }

  @Test
  fun testTitleText() {
    newDialog { title(R.string.app_name) }
    EspressoX.onViewInDialog(withId(R.id.title), withText(R.string.app_name))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testPositiveButton() {
    newDialog { positiveButton(R.string.app_name) }
    EspressoX.onViewInDialog(withId(R.id.positive), withText(R.string.app_name))
      .perform(click())

    onView(isRoot()).inRoot(not(isDialog()))
  }

  @Test
  fun testNegativeButton() {
    newDialog { negativeButton(R.string.app_name) }
    EspressoX.onViewInDialog(withId(R.id.negative), withText(R.string.app_name))
      .perform(click())

    onView(isRoot()).inRoot(not(isDialog()))
  }

  @Test
  fun testNeutralButton() {
    newDialog { neutralButton(android.R.string.copy) }
    EspressoX.onViewInDialog(withId(R.id.neutral), withText(android.R.string.copy))
      .perform(click())

    onView(isRoot()).inRoot(not(isDialog()))
  }

  @Test
  fun testMessageText() {
    newDialog { message(R.string.app_name) }
    EspressoX.onViewInDialog(isDescendantOfA(withId(R.id.content)), withText(R.string.app_name))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testTextInput() {
    val mockValidator = mockk<(String) -> Int>()
    every { mockValidator.invoke("invalid") } returns R.string.app_name
    every { mockValidator.invoke("test") } returns 0
    val dialogFragment = newDialog {
      input(
        hintRes = R.string.app_name,
        preFillValue = "test",
        validator = mockValidator
      )
    }

    EspressoX.onViewInDialog(isDescendantOfA(withId(R.id.content)), withId(R.id.editText))
      .check(matches(isDisplayed()))
      .perform(replaceText("invalid"))

    EspressoX.onViewInDialog(withId(R.id.textInputLayout))
      .check(matches(EspressoX.withErrorText(R.string.app_name)))

    EspressoX.onViewInDialog(withId(R.id.positive))
      .check(matches(not(isEnabled())))

    EspressoX.onViewInDialog(isDescendantOfA(withId(R.id.content)), withId(R.id.editText))
      .perform(replaceText("test"))

    EspressoX.onViewInDialog(withId(R.id.textInputLayout))
      .check(matches(not(EspressoX.withErrorText(R.string.app_name))))

    assertEquals("test", dialogFragment.getInputText())
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
      EspressoX.onViewInDialog(isDescendantOfA(withId(android.R.id.list)), withText(it))
        .check(matches(isDisplayed()))
    }

    EspressoX.onViewInDialog(isDescendantOfA(withId(android.R.id.list)), withText(items[1]))
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

    EspressoX.onViewInDialog(withId(id))
      .check(matches(isDisplayed()))
      .perform(EspressoX.slide(expectedValue))

    EspressoX.onViewInDialog(withId(R.id.positive)).perform(click())
    assertEquals(expectedValue, value)
  }

  private fun newDialog(options: DialogFragment.() -> Unit): DialogFragment {
    val f = emptyFragmentScenario.withFragment {
      DialogFragment.show(childFragmentManager, options)
    }

    // wait for dialog to be visible
    EspressoX.onViewInDialog(withId(R.id.dialog_root))
      .check(matches(isDisplayed()))

    return f
  }
}

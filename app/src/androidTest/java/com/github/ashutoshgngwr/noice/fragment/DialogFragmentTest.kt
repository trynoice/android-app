package com.github.ashutoshgngwr.noice.fragment

import android.view.View
import androidx.annotation.StringRes
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.MainActivity
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.RetryTestRule
import com.google.android.material.textfield.TextInputLayout
import org.hamcrest.Description
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeMatcher
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

  private lateinit var activityScenario: ActivityScenario<MainActivity>
  private lateinit var dialogFragment: DialogFragment

  private fun hasErrorText(@Suppress("SameParameterValue") @StringRes expectedErrorText: Int) =
    object : TypeSafeMatcher<View>() {
      override fun describeTo(description: Description?) = Unit
      override fun matchesSafely(item: View?): Boolean {
        if (item !is TextInputLayout) return false
        val error = item.error ?: return false
        return item.context.getString(expectedErrorText) == error.toString()
      }
    }

  @Before
  fun setup() {
    dialogFragment = DialogFragment()
    activityScenario = ActivityScenario.launch(MainActivity::class.java)
  }

  @Test
  fun testTitleText() {
    activityScenario.onActivity {
      dialogFragment.show(it.supportFragmentManager) {
        title(android.R.string.yes)
      }
    }

    onView(allOf(withId(R.id.title), withText(android.R.string.yes)))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testPositiveButton() {
    activityScenario.onActivity {
      dialogFragment.show(it.supportFragmentManager) {
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
    activityScenario.onActivity {
      dialogFragment.show(it.supportFragmentManager) {
        negativeButton(android.R.string.no)
      }
    }

    EspressoX.waitForView(withId(R.id.negative), 100, 5)
      .check(matches(withText(android.R.string.no)))
      .perform(click())

    onView(withId(R.id.negative)).check(doesNotExist())
  }

  @Test
  fun testMessageText() {
    activityScenario.onActivity {
      dialogFragment.show(it.supportFragmentManager) {
        message(android.R.string.yes)
      }
    }

    onView(allOf(isDescendantOfA(withId(R.id.content)), withText(android.R.string.yes)))
      .check(matches(isDisplayed()))
  }

  @Test
  fun testTextInput() {
    activityScenario.onActivity {
      dialogFragment.show(it.supportFragmentManager) {
        input(
          hintRes = android.R.string.yes,
          preFillValue = "test",
          errorRes = android.R.string.no
        )
      }
    }

    onView(allOf(isDescendantOfA(withId(R.id.content)), withId(R.id.editText)))
      .check(matches(isDisplayed()))
      .perform(replaceText("  "))

    onView(withId(R.id.textInputLayout))
      .check(matches(hasErrorText(android.R.string.no)))

    onView(withId(R.id.positive))
      .check(matches(not(isEnabled())))

    onView(allOf(isDescendantOfA(withId(R.id.content)), withId(R.id.editText)))
      .perform(replaceText("test"))

    onView(withId(R.id.textInputLayout))
      .check(matches(not(hasErrorText(android.R.string.no))))

    assertEquals("test", dialogFragment.getInputText())
  }

  @Test
  fun testSingleChoiceList() {
    var selectedItem = 0
    val items = arrayOf("test-0", "test-1", "test-2")

    activityScenario.onActivity {
      dialogFragment.show(it.supportFragmentManager) {
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

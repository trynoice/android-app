package com.github.ashutoshgngwr.noice

import android.content.Intent
import android.view.View
import android.widget.TimePicker
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.MotionEvents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.util.TreeIterables
import com.github.ashutoshgngwr.noice.widget.DurationPicker
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.*
import org.hamcrest.TypeSafeMatcher

/**
 * [EspressoX] contains the custom extended util implementations for Espresso.
 */
object EspressoX {

  /**
   * [clickInItem] performs a click action on item with the given [viewId] inside currently
   * matched view.
   */
  fun clickInItem(@IdRes viewId: Int): ViewAction {
    return object : ViewAction {
      override fun getDescription() = "Click on view with specified id"
      override fun getConstraints() = hasDescendant(withId(viewId))

      override fun perform(uiController: UiController, view: View) {
        view.findViewById<View>(viewId).also { it.performClick() }
      }
    }
  }

  /**
   * [slide] emulates slide action on the provided [Slider] widget.
   */
  fun slide(value: Float): ViewAction {
    return object : ViewAction {
      override fun getDescription() = "Emulate user input on a slider"
      override fun getConstraints() = instanceOf<View>(Slider::class.java)

      override fun perform(uiController: UiController, view: View) {
        view as Slider
        val height = view.height - view.paddingTop - view.paddingBottom
        val location = intArrayOf(0, 0)
        view.getLocationOnScreen(location)

        val xOffset = location[0].toFloat() + view.paddingStart + view.trackSidePadding
        val range = view.valueTo - view.valueFrom
        val xStart = (((view.value - view.valueFrom) / range) * view.trackWidth) + xOffset

        val x = (((value - view.valueFrom) / range) * view.trackWidth) + xOffset
        val y = location[1] + view.paddingTop + (height.toFloat() / 2)

        val startCoordinates = floatArrayOf(xStart, y)
        val endCoordinates = floatArrayOf(x, y)
        val precision = floatArrayOf(1f, 1f)

        // Send down event, and send up
        val down = MotionEvents.sendDown(uiController, startCoordinates, precision).down
        uiController.loopMainThreadForAtLeast(100)
        MotionEvents.sendMovement(uiController, down, endCoordinates)
        uiController.loopMainThreadForAtLeast(100)
        MotionEvents.sendUp(uiController, down, endCoordinates)
      }
    }
  }

  private fun searchForView(viewMatcher: Matcher<View>): ViewAction {
    return object : ViewAction {
      override fun getConstraints() = isRoot()
      override fun getDescription() = "search for view with $viewMatcher in the root view"

      override fun perform(uiController: UiController, view: View) {
        TreeIterables.breadthFirstViewTraversal(view).forEach {
          if (viewMatcher.matches(it)) {
            return
          }
        }

        throw NoMatchingViewException.Builder()
          .withRootView(view)
          .withViewMatcher(viewMatcher)
          .build()
      }
    }
  }

  /**
   * [waitForView] tries to find a view with given [viewMatchers]. If found, it returns the
   * [ViewInteraction] for given [viewMatchers]. If not found, it waits for given [wait]
   * before attempting to find the view again. It reties for given number of [retries].
   *
   * Adaptation of the [StackOverflow post by manbradcalf](https://stackoverflow.com/a/56499223/2410641)
   */
  fun waitForView(
    vararg viewMatchers: Matcher<View>,
    retries: Int = 5,
    wait: Long = 1000L,
  ): ViewInteraction {
    require(retries > 0 && wait > 0)
    val viewMatcher = allOf(*viewMatchers)
    var i = 0
    while(i++ < retries) {
      try {
        onView(isRoot()).perform(searchForView(viewMatcher))
        break
      } catch (e: NoMatchingViewException) {
        if (i == retries) {
          throw e
        }

        Thread.sleep(wait)
      }
    }

    return onView(viewMatcher)
  }

  /**
   * Returns a [ViewAction] that invokes [DurationPicker.onDurationAddedListener] with the given
   * [durationSecs].
   */
  fun addDurationToPicker(durationSecs: Long): ViewAction {
    return object : ViewAction {
      override fun getDescription() = "add duration to a DurationPicker"
      override fun getConstraints() = instanceOf<View>(DurationPicker::class.java)

      override fun perform(uiController: UiController, view: View) {
        view as DurationPicker
        view.invokeOnDurationAddedListener(durationSecs * 1000L)
      }
    }
  }

  /**
   * Returns a [Matcher] that matches reset button of the [DurationPicker] view.
   */
  fun withDurationPickerResetButton(durationPickerMatcher: Matcher<View>): Matcher<View> {
    return allOf(isDescendantOfA(durationPickerMatcher), withId(R.id.reset_button))
  }

  /**
   * [withErrorText] matches [TextInputLayout]s using the provided error text.
   */
  fun withErrorText(@StringRes expectedErrorText: Int): Matcher<View> {
    return object : TypeSafeMatcher<View>() {
      override fun describeTo(description: Description?) = Unit
      override fun matchesSafely(item: View?): Boolean {
        if (item !is TextInputLayout) return false
        val error = item.error ?: return false
        return item.context.getString(expectedErrorText) == error.toString()
      }
    }
  }

  /**
   * [is24hViewEnabled] matches a [TimePicker] that has the field [TimePicker.is24HourView] enabled.
   */
  fun is24hViewEnabled(): Matcher<View> {
    return object : TypeSafeMatcher<View>() {
      override fun describeTo(description: Description?) = Unit
      override fun matchesSafely(item: View?): Boolean {
        return item is TimePicker && item.is24HourView
      }
    }
  }

  /**
   * Returns a matcher that matches the nested intent sent with an Intent chooser.
   */
  fun hasIntentChooser(matcher: Matcher<Intent>): Matcher<Intent> {
    return allOf(
      hasAction(Intent.ACTION_CHOOSER),
      hasExtra(`is`(Intent.EXTRA_INTENT), matcher)
    )
  }

  /**
   * [noop] returns a [ViewAction] that does nothing.
   */
  fun noop(): ViewAction {
    return object : ViewAction {
      override fun getDescription() = "no-op"
      override fun getConstraints() = any(View::class.java)
      override fun perform(uiController: UiController, view: View) = Unit
    }
  }
}

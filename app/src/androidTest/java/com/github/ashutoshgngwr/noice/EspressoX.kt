package com.github.ashutoshgngwr.noice

import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.action.MotionEvents
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.util.TreeIterables
import com.github.ashutoshgngwr.noice.widget.DurationPicker
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.instanceOf
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
   * [slideInItem] performs a slide action on a [Slider] with given [sliderID] inside currently
   * matched view.
   */
  fun slideInItem(@IdRes sliderID: Int, value: Float): ViewAction {
    return object : ViewAction {
      override fun getDescription() = "Emulate user input on a seek bar"
      override fun getConstraints() =
        hasDescendant(allOf(withId(sliderID), instanceOf(Slider::class.java)))

      override fun perform(uiController: UiController, view: View) {
        val slider = view.findViewById<Slider>(sliderID)
        val height = slider.height - slider.paddingTop - slider.paddingBottom

        val location = intArrayOf(0, 0)
        slider.getLocationOnScreen(location)

        val xOffset = location[0].toFloat() + slider.paddingStart + slider.trackSidePadding
        val range = slider.valueTo - slider.valueFrom
        val xStart = (((slider.value - slider.valueFrom) / range) * slider.trackWidth) + xOffset

        val x = (((value - slider.valueFrom) / range) * slider.trackWidth) + xOffset
        val y = location[1] + slider.paddingTop + (height.toFloat() / 2)

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
   * [waitForView] tries to find a view with given [viewMatcher]. If found, it returns the
   * [ViewInteraction] for the given [viewMatcher]. If not found, it waits for given [periodMillis]
   * before attempting to find the view again. It reties for given number of [retries].
   *
   * Adaptation of the [StackOverflow post by manbradcalf](https://stackoverflow.com/a/56499223/2410641)
   */
  fun waitForView(viewMatcher: Matcher<View>, periodMillis: Long, retries: Int): ViewInteraction {
    require(retries > 0 && periodMillis > 0)
    for (i in 1..retries) {
      try {
        onView(isRoot()).perform(searchForView(viewMatcher))
        return onView(viewMatcher)
      } catch (e: NoMatchingViewException) {
        if (i == retries) {
          throw e
        }

        Thread.sleep(periodMillis)
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
}

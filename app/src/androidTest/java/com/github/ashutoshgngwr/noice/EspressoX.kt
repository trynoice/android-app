package com.github.ashutoshgngwr.noice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.MotionEvents
import androidx.test.espresso.matcher.ViewMatchers.*
import com.github.ashutoshgngwr.noice.widget.DurationPicker
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.TypeSafeMatcher
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.Closeable


/**
 * [EspressoX] contains the custom extended util implementations for Espresso.
 */
object EspressoX {

  /**
   * [clickOn] performs a click action on item with the given [viewId] inside currently matched
   * view.
   */
  fun clickOn(@IdRes viewId: Int): ViewAction {
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

  fun itemAtPosition(position: Int, @IdRes viewId: Int, assertion: ViewAssertion): ViewAssertion {
    return ViewAssertion { view: View, noViewException: NoMatchingViewException? ->
      if (noViewException != null) {
        throw noViewException
      }

      assertTrue(view is RecyclerView)
      val targetView = (view as? RecyclerView)?.findViewHolderForAdapterPosition(position)
        ?.itemView
        ?.findViewById<View?>(viewId)

      if (targetView == null) {
        fail("position [$position] doesn't exist in Adapter")
        return@ViewAssertion
      }

      assertion.check(targetView, null)
    }
  }

  fun withBottomNavSelectedItem(@IdRes id: Int): Matcher<View> = object : TypeSafeMatcher<View>() {
    override fun describeTo(description: Description?) = Unit
    override fun matchesSafely(item: View?): Boolean {
      return item is BottomNavigationView && item.selectedItemId == id
    }
  }

  /**
   * https://github.com/android/architecture-samples/blob/2291fc6d2e17a37be584a89b80ee73c207c804c3/app/src/androidTest/java/com/example/android/architecture/blueprints/todoapp/HiltExt.kt#L28-L65
   */
  inline fun <reified F : Fragment> launchFragmentInHiltContainer(
    fragmentArgs: Bundle? = null,
    @StyleRes themeResId: Int = R.style.Theme_App,
  ): HiltFragmentScenario<F> {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val intent = Intent.makeMainActivity(ComponentName(context, HiltTestActivity::class.java))
      .putExtra(
        "androidx.fragment.app.testing.FragmentScenario.EmptyFragmentActivity.THEME_EXTRAS_BUNDLE_KEY",
        themeResId
      )

    lateinit var fragment: F
    val activityScenario = ActivityScenario.launch<HiltTestActivity>(intent)
      .onActivity { activity ->
        fragment = activity.supportFragmentManager
          .fragmentFactory
          .instantiate(requireNotNull(F::class.java.classLoader), F::class.java.name) as F

        fragment.arguments = fragmentArgs
        activity.supportFragmentManager
          .beginTransaction()
          .add(android.R.id.content, fragment, F::class.java.simpleName)
          .commitNow()
      }

    return HiltFragmentScenario(activityScenario, fragment)
  }
}

/**
 * Mimics the `FragmentScenario` class from `fragment-testing` lib, but uses [HiltTestActivity]
 * under the hood.
 */
class HiltFragmentScenario<F : Fragment>(
  private val activityScenario: ActivityScenario<HiltTestActivity>,
  private val fragment: F,
) : Closeable {

  fun onFragment(action: (F) -> Unit): HiltFragmentScenario<F> {
    activityScenario.onActivity { action.invoke(fragment) }
    return this
  }

  override fun close() {
    activityScenario.close()
  }
}

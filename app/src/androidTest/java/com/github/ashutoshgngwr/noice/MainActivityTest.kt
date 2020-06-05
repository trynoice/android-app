package com.github.ashutoshgngwr.noice

import android.content.Intent
import android.net.Uri
import android.view.Gravity
import androidx.appcompat.app.AppCompatDelegate
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.launch
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.DrawerMatchers.isClosed
import androidx.test.espresso.contrib.NavigationViewActions
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.filterEquals
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.ashutoshgngwr.noice.fragment.AboutFragment
import com.github.ashutoshgngwr.noice.fragment.PresetFragment
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment
import kotlinx.android.synthetic.main.activity_main.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Thread.sleep

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  private lateinit var activityScenario: ActivityScenario<MainActivity>

  @Before
  fun setup() {
    activityScenario = launch(MainActivity::class.java)
  }

  @Test
  fun testIfSoundLibraryIsVisibleOnStart() {
    activityScenario.onActivity {
      assertEquals(
        SoundLibraryFragment::class.java.simpleName,
        it.supportFragmentManager.getBackStackEntryAt(it.supportFragmentManager.backStackEntryCount - 1).name
      )

      assertEquals(R.id.library, it.navigation_drawer.checkedItem?.itemId)
    }
  }

  @Test
  fun testSavedPresetMenuItem() {
    onView(withId(R.id.layout_main))
      .check(matches(isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    onView(withId(R.id.navigation_drawer))
      .perform(NavigationViewActions.navigateTo(R.id.saved_presets))

    activityScenario.onActivity {
      assertEquals(
        PresetFragment::class.java.simpleName,
        it.supportFragmentManager.getBackStackEntryAt(it.supportFragmentManager.backStackEntryCount - 1).name
      )

      assertEquals(R.id.saved_presets, it.navigation_drawer.checkedItem?.itemId)
    }
  }

  @Test
  fun testThemeMenuItem() {
    val nightModes = arrayOf(
      AppCompatDelegate.MODE_NIGHT_NO,
      AppCompatDelegate.MODE_NIGHT_YES,
      AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    )

    val themes = InstrumentationRegistry.getInstrumentation()
      .targetContext
      .resources
      .getStringArray(R.array.app_themes)

    for (i in themes.indices) {
      onView(withId(R.id.layout_main))
        .check(matches(isClosed(Gravity.START)))
        .perform(DrawerActions.open(Gravity.START))

      onView(withId(R.id.navigation_drawer))
        .perform(NavigationViewActions.navigateTo(R.id.app_theme))

      onView(withText(themes[i]))
        .inRoot(isDialog())
        .check(matches(isDisplayed()))
        .perform(click())

      onView(withId(R.id.layout_main)).check(matches(isDisplayed())) // wait for activity to recreate
      assertEquals(nightModes[i], AppCompatDelegate.getDefaultNightMode())
    }
  }

  @Test
  fun testAboutMenuItem() {
    onView(withId(R.id.layout_main))
      .check(matches(isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    onView(withId(R.id.navigation_drawer))
      .perform(NavigationViewActions.navigateTo(R.id.about))

    activityScenario.onActivity {
      assertEquals(
        AboutFragment::class.java.simpleName,
        it.supportFragmentManager.getBackStackEntryAt(it.supportFragmentManager.backStackEntryCount - 1).name
      )

      assertEquals(R.id.about, it.navigation_drawer.checkedItem?.itemId)
    }
  }

  @Test
  fun testReportIssuesMenuItem() {
    Intents.init()
    onView(withId(R.id.layout_main))
      .check(matches(isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    onView(withId(R.id.navigation_drawer))
      .perform(NavigationViewActions.navigateTo(R.id.report_issue))

    intended(
      filterEquals(
        Intent(
          Intent.ACTION_VIEW, Uri.parse(
            InstrumentationRegistry.getInstrumentation()
              .targetContext
              .getString(R.string.app_issues_url)
          )
        )
      )
    )

    Intents.release()
  }

  @Test
  fun testRateOnPlayStoreMenuItem() {
    Intents.init()
    onView(withId(R.id.layout_main))
      .check(matches(isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    onView(withId(R.id.navigation_drawer))
      .perform(NavigationViewActions.navigateTo(R.id.rate_on_play_store))

    intended(
      filterEquals(
        Intent(
          Intent.ACTION_VIEW,
          Uri.parse("market://details?id=${InstrumentationRegistry.getInstrumentation().targetContext.packageName}")
        )
      )
    )

    Intents.release()
  }

  @Test
  fun testBackNavigation() {
    val drawerIdlingResource = CountingIdlingResource("CloseNavigationDrawer")
    activityScenario.onActivity {
      it.layout_main.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
        override fun onDrawerStateChanged(newState: Int) {
          // this only works because we're never in dragging state. otherwise, this would fail.
          if (newState == DrawerLayout.STATE_IDLE) {
            drawerIdlingResource.decrement()
          } else if (newState == DrawerLayout.STATE_SETTLING) {
            drawerIdlingResource.increment()
          }
        }
      })
    }

    IdlingRegistry.getInstance().register(drawerIdlingResource)
    onView(withId(R.id.layout_main))
      .check(matches(isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))
    onView(withId(R.id.navigation_drawer)).perform(NavigationViewActions.navigateTo(R.id.saved_presets))

    onView(withId(R.id.layout_main))
      .check(matches(isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))
    onView(withId(R.id.navigation_drawer)).perform(NavigationViewActions.navigateTo(R.id.about))

    onView(withId(R.id.layout_main)).check(matches(isClosed(Gravity.START)))
    IdlingRegistry.getInstance().unregister(drawerIdlingResource)

    activityScenario.onActivity {
      it.onBackPressed()
      assertEquals(
        PresetFragment::class.java.simpleName,
        it.supportFragmentManager
          .getBackStackEntryAt(it.supportFragmentManager.backStackEntryCount - 1)
          .name
      )

      it.onBackPressed()
      assertEquals(
        SoundLibraryFragment::class.java.simpleName,
        it.supportFragmentManager
          .getBackStackEntryAt(it.supportFragmentManager.backStackEntryCount - 1)
          .name
      )

      // ensure that fragments are not present in the back stack
      assertFalse(
        it.supportFragmentManager.popBackStackImmediate(
          AboutFragment::class.java.simpleName,
          FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
      )

      assertFalse(
        it.supportFragmentManager.popBackStackImmediate(
          PresetFragment::class.java.simpleName,
          FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
      )

      it.onBackPressed()
      assertTrue(it.isFinishing || it.isDestroyed)
    }
  }

  @Test
  fun testReClickingNavigationItemForFragments() {
    // should have just 1 entry back stack
    activityScenario.onActivity {
      assertEquals(1, it.supportFragmentManager.backStackEntryCount)
    }

    // open nav drawer
    onView(withId(R.id.layout_main))
      .check(matches(isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    // open a new fragment using the menu item
    onView(withId(R.id.navigation_drawer)).perform(NavigationViewActions.navigateTo(R.id.about))
    activityScenario.onActivity {
      assertEquals(2, it.supportFragmentManager.backStackEntryCount)
    }

    sleep(1000L) // tests fail complaining drawer is open. (don't fail always but sometimes it can make you question your entire life.)
    // reopen nav drawer
    onView(withId(R.id.layout_main))
      .check(matches(isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    // try to reselect the same nav item
    onView(withId(R.id.navigation_drawer)).perform(NavigationViewActions.navigateTo(R.id.about))
    activityScenario.onActivity {
      // back stack entry count should remain the same
      assertEquals(2, it.supportFragmentManager.backStackEntryCount)
    }
  }
}

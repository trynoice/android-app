package com.github.ashutoshgngwr.noice

import android.content.Intent
import android.view.Gravity
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.DrawerMatchers
import androidx.test.espresso.contrib.NavigationViewActions
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.github.ashutoshgngwr.noice.fragment.PresetFragment
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment
import com.github.ashutoshgngwr.noice.sound.Preset
import com.github.ashutoshgngwr.noice.sound.player.Player
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.Matchers.allOf
import org.junit.*
import org.junit.Assume.assumeNotNull
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.locale.LocaleTestRule

@RunWith(AndroidJUnit4::class)
class GenerateScreenshots {

  companion object {
    private const val IS_SCREENGRAB = "is_screengrab"
    private const val SLEEP_PERIOD_BEFORE_SCREENGRAB = 500L

    @JvmField
    @ClassRule
    val localeTestRule = LocaleTestRule()

    @JvmStatic
    @BeforeClass
    fun setupAll() {
      // Screengrab file passes the following launch argument. This test class won't run otherwise.
      assumeNotNull(InstrumentationRegistry.getArguments().getString(IS_SCREENGRAB))

      // using mocks to save a few presets for screenshots
      Preset.writeAllToUserPreferences(
        ApplicationProvider.getApplicationContext(), arrayListOf(
          mockk {
            every { name } returns "Airplane"
            every { playerStates } returns arrayOf(
              mockk {
                every { soundKey } returns "airplane_inflight"
                every { volume } returns Player.DEFAULT_VOLUME
                every { timePeriod } returns Player.DEFAULT_TIME_PERIOD
              },
              mockk {
                every { soundKey } returns "airplane_seatbelt_beeps"
                every { volume } returns Player.DEFAULT_VOLUME
                every { timePeriod } returns Player.DEFAULT_TIME_PERIOD
              }
            )
          },
          mockk {
            every { name } returns "Night in the Jungle"
            every { playerStates } returns arrayOf(
              mockk {
                every { soundKey } returns "night"
                every { volume } returns Player.DEFAULT_VOLUME
                every { timePeriod } returns Player.DEFAULT_TIME_PERIOD
              }
            )
          },
          mockk {
            every { name } returns "Windy Summer"
            every { playerStates } returns arrayOf(
              mockk {
                every { soundKey } returns "soft_wind"
                every { volume } returns Player.DEFAULT_VOLUME
                every { timePeriod } returns Player.DEFAULT_TIME_PERIOD
              },
              mockk {
                every { soundKey } returns "wind_in_palm_trees"
                every { volume } returns Player.DEFAULT_VOLUME
                every { timePeriod } returns Player.DEFAULT_TIME_PERIOD
              }
            )
          }
        )
      )
    }

    @JvmStatic
    @AfterClass
    fun teardownAll() {
      // clear saved presets
      Preset.writeAllToUserPreferences(ApplicationProvider.getApplicationContext(), arrayListOf())
    }
  }

  @JvmField
  @Rule
  var activityRule = ActivityTestRule(MainActivity::class.java, true)

  @After
  fun after() {
    activityRule.run {
      activityRule.activity.stopService(Intent(activity, MediaPlayerService::class.java))
    }
  }

  @Test
  fun soundLibrary() {
    onView(withId(R.id.list_sound)).perform(
      actionOnItemAtPosition<SoundLibraryFragment.ViewHolder>(
        0, EspressoX.clickOn(R.id.button_play)
      )
    )

    onView(withId(R.id.list_sound)).perform(
      actionOnItemAtPosition<SoundLibraryFragment.ViewHolder>(
        0, EspressoX.seekProgress(R.id.seekbar_volume, Player.MAX_VOLUME - Player.DEFAULT_VOLUME)
      )
    )

    onView(withId(R.id.list_sound)).perform(
      actionOnItemAtPosition<SoundLibraryFragment.ViewHolder>(
        1, EspressoX.clickOn(R.id.button_play)
      )
    )

    onView(withId(R.id.list_sound)).perform(
      actionOnItemAtPosition<SoundLibraryFragment.ViewHolder>(
        1, EspressoX.seekProgress(R.id.seekbar_volume, Player.MAX_VOLUME)
      )
    )

    onView(withId(R.id.list_sound)).perform(
      actionOnItemAtPosition<SoundLibraryFragment.ViewHolder>(
        1,
        EspressoX.seekProgress(
          R.id.seekbar_time_period,
          Player.MAX_TIME_PERIOD - Player.MIN_TIME_PERIOD - 30
        )
      )
    )

    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("1")
  }

  @Test
  fun savedPresets() {
    onView(withId(R.id.layout_main))
      .check(matches(DrawerMatchers.isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    EspressoX.waitForView(withId(R.id.navigation_drawer), 100, 5)
      .perform(NavigationViewActions.navigateTo(R.id.saved_presets))

    EspressoX.waitForView(withId(R.id.list_presets), 100, 5)
      .perform(
        actionOnItemAtPosition<PresetFragment.ViewHolder>(1, EspressoX.clickOn(R.id.button_play))
      )

    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("2")
  }

  @Test
  fun sleepTimer() {
    onView(withId(R.id.list_sound)).perform(
      actionOnItemAtPosition<SoundLibraryFragment.ViewHolder>(
        0, EspressoX.clickOn(R.id.button_play)
      )
    )

    onView(withId(R.id.layout_main))
      .check(matches(DrawerMatchers.isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    EspressoX.waitForView(withId(R.id.navigation_drawer), 100, 5)
      .perform(NavigationViewActions.navigateTo(R.id.sleep_timer))

    onView(withId(R.id.duration_picker)).perform(
      EspressoX.addDurationToPicker(1800)
    )

    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("3")
  }

  @Test
  fun wakeUpTimer() {
    // cancel any previous alarms
    WakeUpTimerManager.cancel(ApplicationProvider.getApplicationContext())

    onView(withId(R.id.layout_main))
      .check(matches(DrawerMatchers.isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    EspressoX.waitForView(withId(R.id.navigation_drawer), 100, 5)
      .perform(NavigationViewActions.navigateTo(R.id.wake_up_timer))

    EspressoX.waitForView(withId(R.id.button_select_preset), 100, 5)
      .perform(click())

    EspressoX.waitForView(withText("Airplane"), 100, 5)
      .perform(click())

    onView(withId(R.id.duration_picker))
      .perform(EspressoX.addDurationToPicker(1800))

    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("4")
  }

  @Test
  fun about() {
    onView(withId(R.id.layout_main))
      .check(matches(DrawerMatchers.isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    EspressoX.waitForView(withId(R.id.navigation_drawer), 100, 5)
      .perform(NavigationViewActions.navigateTo(R.id.about))

    EspressoX.waitForView(withText(R.string.app_description), 100, 5)
    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("5")
  }

  @Test
  fun navigation() {
    onView(withId(R.id.layout_main))
      .check(matches(DrawerMatchers.isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    EspressoX.waitForView(allOf(withId(R.id.layout_main), DrawerMatchers.isOpen()), 100, 5)
    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("6")
  }

  @Test
  fun themeItem() {
    onView(withId(R.id.layout_main))
      .check(matches(DrawerMatchers.isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    EspressoX.waitForView(withId(R.id.navigation_drawer), 100, 5)
      .perform(NavigationViewActions.navigateTo(R.id.app_theme))

    EspressoX.waitForView(withId(R.id.positive), 100, 5) // wait for dialog
    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("7")
  }
}

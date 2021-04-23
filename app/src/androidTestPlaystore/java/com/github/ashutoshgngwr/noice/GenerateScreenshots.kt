package com.github.ashutoshgngwr.noice

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.contrib.DrawerMatchers
import androidx.test.espresso.contrib.NavigationViewActions
import androidx.test.espresso.contrib.PickerActions
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.ashutoshgngwr.noice.cast.CastAPIWrapper
import com.github.ashutoshgngwr.noice.fragment.PresetFragment
import com.github.ashutoshgngwr.noice.sound.Preset
import com.github.ashutoshgngwr.noice.sound.player.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.AfterClass
import org.junit.Assume.assumeNotNull
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.locale.LocaleTestRule
import java.util.*

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
      // prevent app intro from showing up
      PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
        .edit(commit = true) {
          putBoolean(AppIntroActivity.PREF_HAS_USER_SEEN_APP_INTRO, true)
        }

      // using mocks to save a few presets for screenshots
      Preset.writeAllToUserPreferences(
        ApplicationProvider.getApplicationContext(), arrayListOf(
          mockk {
            every { id } returns UUID.randomUUID().toString()
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
            every { id } returns UUID.randomUUID().toString()
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
            every { id } returns UUID.randomUUID().toString()
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
  var activityScenarioRule = ActivityScenarioRule(MainActivity::class.java)

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  @Rule
  @JvmField
  val screenshotRule = TestRule { base, _ ->
    object : Statement() {
      override fun evaluate() {
        // Screengrabfile passes the following launch argument. The tests in this call won't run
        // otherwise.
        assumeNotNull(InstrumentationRegistry.getArguments().getString(IS_SCREENGRAB))
        base.evaluate()
      }
    }
  }

  @After
  fun after() {
    activityScenarioRule.scenario.onActivity {
      it.stopService(Intent(it, MediaPlayerService::class.java))
    }
  }

  @Test
  fun soundLibrary() {
    // add a fake Cast button since we can't make the real one appear on an emulator.
    mockkObject(CastAPIWrapper.Companion)
    every { CastAPIWrapper.from(any(), any()) } returns mockk(relaxed = true) {
      every { setUpMenuItem(any(), any()) } answers {
        firstArg<Menu>().add("fake-cast-button")
          .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
          .setIcon(R.drawable.cast_ic_notification_small_icon)
          .setIconTintList(
            ColorStateList.valueOf(
              ApplicationProvider.getApplicationContext<Context>()
                .getColor(R.color.action_menu_item)
            )
          )
      }
    }

    activityScenarioRule.scenario.recreate()
    onView(withId(R.id.sound_list)).perform(
      actionOnItem<RecyclerView.ViewHolder>(
        ViewMatchers.hasDescendant(allOf(withId(R.id.title), withText(R.string.light_rain))),
        EspressoX.clickInItem(R.id.play_button)
      )
    )

    onView(withId(R.id.sound_list)).perform(
      actionOnItem<RecyclerView.ViewHolder>(
        ViewMatchers.hasDescendant(allOf(withId(R.id.title), withText(R.string.light_rain))),
        EspressoX.slideInItem(
          R.id.volume_slider,
          Player.MAX_VOLUME.toFloat() - Player.DEFAULT_VOLUME
        )
      )
    )

    onView(withId(R.id.sound_list)).perform(
      actionOnItem<RecyclerView.ViewHolder>(
        ViewMatchers.hasDescendant(
          allOf(withId(R.id.title), withText(R.string.distant_thunder))
        ),
        EspressoX.clickInItem(R.id.play_button)
      )
    )

    onView(withId(R.id.sound_list)).perform(
      actionOnItem<RecyclerView.ViewHolder>(
        ViewMatchers.hasDescendant(
          allOf(withId(R.id.title), withText(R.string.distant_thunder))
        ),
        EspressoX.slideInItem(R.id.volume_slider, Player.MAX_VOLUME.toFloat())
      )
    )

    onView(withId(R.id.sound_list)).perform(
      actionOnItem<RecyclerView.ViewHolder>(
        ViewMatchers.hasDescendant(
          allOf(withId(R.id.title), withText(R.string.distant_thunder))
        ),
        EspressoX.slideInItem(R.id.time_period_slider, Player.MAX_TIME_PERIOD.toFloat() - 300)
      )
    )

    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("1")
    unmockkObject(CastAPIWrapper.Companion)
  }

  @Test
  fun savedPresets() {
    onView(withId(R.id.layout_main))
      .check(matches(DrawerMatchers.isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    EspressoX.waitForView(withId(R.id.navigation_drawer))
      .perform(NavigationViewActions.navigateTo(R.id.saved_presets))

    EspressoX.waitForView(withId(R.id.preset_list))
      .perform(
        actionOnItemAtPosition<PresetFragment.ViewHolder>(
          1, EspressoX.clickInItem(R.id.play_button)
        )
      )

    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("2")
  }

  @Test
  fun sleepTimer() {
    onView(withId(R.id.sound_list)).perform(
      actionOnItem<RecyclerView.ViewHolder>(
        ViewMatchers.hasDescendant(allOf(withId(R.id.title), withText(R.string.birds))),
        EspressoX.clickInItem(R.id.play_button)
      )
    )

    onView(withId(R.id.layout_main))
      .check(matches(DrawerMatchers.isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    EspressoX.waitForView(withId(R.id.navigation_drawer))
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

    EspressoX.waitForView(withId(R.id.navigation_drawer))
      .perform(NavigationViewActions.navigateTo(R.id.wake_up_timer))

    EspressoX.waitForView(withId(R.id.select_preset_button))
      .perform(scrollTo(), click())

    EspressoX.waitForView(withText("Airplane"))
      .perform(scrollTo(), click())

    onView(withId(R.id.time_picker))
      .perform(PickerActions.setTime(12, 30))

    onView(withId(R.id.set_time_button))
      .perform(scrollTo(), click())

    onView(withText(R.string.wake_up_timer_description))
      .perform(scrollTo())

    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("4")
  }

  @Test
  fun about() {
    onView(withId(R.id.layout_main))
      .check(matches(DrawerMatchers.isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    EspressoX.waitForView(withId(R.id.navigation_drawer))
      .perform(NavigationViewActions.navigateTo(R.id.about))

    EspressoX.waitForView(withText(R.string.app_description))
    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("5")
  }

  @Test
  fun navigation() {
    onView(withId(R.id.layout_main))
      .check(matches(DrawerMatchers.isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    EspressoX.waitForView(allOf(withId(R.id.layout_main), DrawerMatchers.isOpen()))
    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("6")
  }

  @Test
  fun themeItem() {
    onView(withId(R.id.layout_main))
      .check(matches(DrawerMatchers.isClosed(Gravity.START)))
      .perform(DrawerActions.open(Gravity.START))

    EspressoX.waitForView(withId(R.id.navigation_drawer))
      .perform(NavigationViewActions.navigateTo(R.id.app_theme))

    EspressoX.waitForView(withId(R.id.positive)) // wait for dialog
    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("7")
  }
}

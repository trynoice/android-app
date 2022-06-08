package com.github.ashutoshgngwr.noice

import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.contrib.PickerActions
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.ashutoshgngwr.noice.EspressoX.onViewInDialog
import com.github.ashutoshgngwr.noice.activity.AppIntroActivity
import com.github.ashutoshgngwr.noice.activity.MainActivity
import com.github.ashutoshgngwr.noice.fragment.PresetsFragment
import com.github.ashutoshgngwr.noice.playback.Player
import com.github.ashutoshgngwr.noice.provider.CastApiProvider
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.every
import io.mockk.mockk
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.AfterClass
import org.junit.Assume.assumeNotNull
import org.junit.Before
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.cleanstatusbar.CleanStatusBar
import tools.fastlane.screengrab.locale.LocaleTestRule
import javax.inject.Inject

@HiltAndroidTest
@UninstallModules(CastApiProviderModule::class)
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
      CleanStatusBar.enableWithDefaults()

      // prevent app intro and data consent from showing up
      PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
        .edit {
          clear() // clear any existing preferences.
          putBoolean(AppIntroActivity.PREF_HAS_USER_SEEN_APP_INTRO, true)
          putBoolean(MainActivity.PREF_HAS_SEEN_DATA_COLLECTION_CONSENT, true)
        }
    }

    @JvmStatic
    @AfterClass
    fun teardownAll() {
      CleanStatusBar.disable()

      // clear saved presets
      PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext())
        .edit { clear() }
    }
  }

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @get:Rule
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

  private lateinit var activityScenario: ActivityScenario<MainActivity>

  @BindValue
  internal lateinit var mockCastApiProvider: CastApiProvider

  @set:Inject
  internal lateinit var wakeUpTimerManager: WakeUpTimerManager

  @Before
  fun before() {
    hiltRule.inject()
    mockCastApiProvider = mockk(relaxed = true)
    activityScenario = ActivityScenario.launch(MainActivity::class.java)
  }

  @After
  fun after() {
    activityScenario.onActivity {
      it.stopService(Intent(it, MediaPlayerService::class.java))
    }
  }

  @Test
  fun library() {
    // add a fake Cast button since we can't make the real one appear on an emulator.
    every { mockCastApiProvider.addMenuItem(any(), any(), any()) } answers {
      secondArg<Menu>().add("fake-cast-button").apply {
        setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        setIcon(R.drawable.cast_ic_notification_small_icon)
      }
    }

    activityScenario.recreate()
    onView(withId(R.id.sound_list)).perform(
      actionOnItem<RecyclerView.ViewHolder>(
        ViewMatchers.hasDescendant(allOf(withId(R.id.title), withText(R.string.light_rain))),
        click()
      )
    )

    onView(withId(R.id.sound_list)).perform(
      actionOnItem<RecyclerView.ViewHolder>(
        ViewMatchers.hasDescendant(allOf(withId(R.id.title), withText(R.string.light_rain))),
        EspressoX.clickOn(R.id.volume)
      )
    )

    onViewInDialog(withId(R.id.volume_slider))
      .perform(EspressoX.slide(Player.MAX_VOLUME.toFloat() - Player.DEFAULT_VOLUME))

    onViewInDialog(withId(R.id.positive))
      .perform(click())

    onView(withId(R.id.sound_list)).perform(
      actionOnItem<RecyclerView.ViewHolder>(
        ViewMatchers.hasDescendant(
          allOf(withId(R.id.title), withText(R.string.distant_thunder))
        ),
        click()
      )
    )

    onView(withId(R.id.sound_list)).perform(
      actionOnItem<RecyclerView.ViewHolder>(
        ViewMatchers.hasDescendant(allOf(withId(R.id.title), withText(R.string.distant_thunder))),
        EspressoX.clickOn(R.id.volume)
      )
    )

    onViewInDialog(withId(R.id.volume_slider))
      .perform(EspressoX.slide(Player.MAX_VOLUME.toFloat()))

    onViewInDialog(withId(R.id.positive))
      .perform(click())

    onView(withId(R.id.sound_list)).perform(
      actionOnItem<RecyclerView.ViewHolder>(
        ViewMatchers.hasDescendant(allOf(withId(R.id.title), withText(R.string.distant_thunder))),
        EspressoX.clickOn(R.id.time_period)
      )
    )

    onViewInDialog(withId(R.id.time_period_slider))
      .perform(EspressoX.slide(Player.MAX_TIME_PERIOD.toFloat() - 300))

    onViewInDialog(withId(R.id.positive))
      .perform(click())

    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("1")
  }

  @Test
  fun presets() {
    onView(withId(R.id.presets)).perform(click())
    onView(withId(R.id.list))
      .perform(
        actionOnItemAtPosition<PresetsFragment.ViewHolder>(
          1, EspressoX.clickOn(R.id.play_button)
        )
      )

    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("2")
  }

  @Test
  fun wakeUpTimer() {
    // cancel any previous alarm
    wakeUpTimerManager.cancel()

    onView(withId(R.id.wake_up_timer)).perform(click())

    onView(withId(R.id.time_picker))
      .perform(PickerActions.setTime(12, 30))

    onView(withId(R.id.set_time_button))
      .perform(scrollTo(), click())

    onView(withText(R.string.wake_up_timer_description))
      .perform(scrollTo())

    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("3")
  }

  @Test
  fun sleepTimer() {
    onView(withId(R.id.sound_list)).perform(
      actionOnItem<RecyclerView.ViewHolder>(
        ViewMatchers.hasDescendant(allOf(withId(R.id.title), withText(R.string.birds))),
        click()
      )
    )

    onView(withId(R.id.sleep_timer)).perform(click())
    onView(withId(R.id.duration_picker)).perform(
      EspressoX.addDurationToPicker(1800)
    )

    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("4")
  }

  @Test
  fun about() {
    openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
    onView(withText(R.string.about)).perform(click())
    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("5")
  }

  @Test
  fun settingsItem() {
    openActionBarOverflowOrOptionsMenu(ApplicationProvider.getApplicationContext())
    onView(withText(R.string.settings)).perform(click())
    Thread.sleep(SLEEP_PERIOD_BEFORE_SCREENGRAB)
    Screengrab.screenshot("6")
  }
}

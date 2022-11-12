package com.github.ashutoshgngwr.noice.fragment

//import androidx.test.espresso.Espresso.onView
//import androidx.test.espresso.action.ViewActions
//import androidx.test.espresso.matcher.ViewMatchers.withId
//import androidx.test.ext.junit.runners.AndroidJUnit4
//import com.github.ashutoshgngwr.noice.EspressoX.launchFragmentInHiltContainer
//import com.github.ashutoshgngwr.noice.R
//import com.github.ashutoshgngwr.noice.model.Sound
//import com.github.ashutoshgngwr.noice.playback.PlaybackController
//import dagger.hilt.android.testing.BindValue
//import dagger.hilt.android.testing.HiltAndroidRule
//import dagger.hilt.android.testing.HiltAndroidTest
//import io.mockk.mockk
//import io.mockk.verify
//import org.junit.Before
//import org.junit.Rule
//import org.junit.Test
//import org.junit.runner.RunWith
//
//@HiltAndroidTest
//@RunWith(AndroidJUnit4::class)
//class RandomPresetFragmentTest {
//
//  @get:Rule
//  val hiltRule = HiltAndroidRule(this)
//
//  @BindValue
//  internal lateinit var mockPlaybackController: PlaybackController
//
//  @Before
//  fun setup() {
//    mockPlaybackController = mockk(relaxed = true)
//  }
//
//  @Test
//  fun testRandomPresetButton_onClick() {
//    val intensities = mapOf(
//      R.id.preset_intensity__any to RandomPresetFragment.RANGE_INTENSITY_ANY,
//      R.id.preset_intensity__dense to RandomPresetFragment.RANGE_INTENSITY_DENSE,
//      R.id.preset_intensity__light to RandomPresetFragment.RANGE_INTENSITY_LIGHT
//    )
//
//    val types = mapOf(
//      R.id.preset_type__any to null,
//      R.id.preset_type__focus to Sound.Tag.FOCUS,
//      R.id.preset_type__relax to Sound.Tag.RELAX
//    )
//
//    launchFragmentInHiltContainer<RandomPresetFragment>(null, R.style.Theme_App)
//
//    val intensityID = intensities.keys.random()
//    val typeID = types.keys.random()
//
//    onView(withId(typeID)).perform(ViewActions.click())
//    onView(withId(intensityID)).perform(ViewActions.click())
//
//    onView(withId(R.id.play_button))
//      .perform(ViewActions.click())
//
//    verify(exactly = 1) {
//      mockPlaybackController.playRandomPreset(
//        types[typeID],
//        intensities[intensityID] ?: IntRange.EMPTY
//      )
//    }
//  }
//}

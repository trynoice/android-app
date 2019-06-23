package com.github.ashutoshgngwr.noice

import android.content.Intent
import android.net.Uri
import androidx.core.view.GravityCompat
import androidx.fragment.app.FragmentManager
import com.github.ashutoshgngwr.noice.fragment.AboutFragment
import com.github.ashutoshgngwr.noice.fragment.PresetFragment
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment
import kotlinx.android.synthetic.main.activity_main.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.fakes.RoboMenuItem

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

  private lateinit var mainActivity: MainActivity

  @Before
  fun setup() {
    mainActivity = Robolectric
      .buildActivity(MainActivity::class.java)
      .create()
      .get()
  }

  @Test
  fun `drawer - should be closed at start`() {
    assert(!mainActivity.layout_main.isDrawerOpen(GravityCompat.START))
  }

  @Test
  fun `drawer - should open-close on clicking toolbar home button`() {
    val toolbarHome = RoboMenuItem(android.R.id.home)

    mainActivity.onOptionsItemSelected(toolbarHome)
    assert(
      mainActivity
        .layout_main
        .isDrawerOpen(GravityCompat.START)
    )

    mainActivity.onOptionsItemSelected(toolbarHome)
    assert(
      !mainActivity
        .layout_main
        .isDrawerOpen(GravityCompat.START)
    )
  }

  @Test
  fun `drawer - should close on pressing back button if open`() {
    mainActivity.layout_main.openDrawer(GravityCompat.START)
    mainActivity.onBackPressed()
    assert(!mainActivity.layout_main.isDrawerOpen(GravityCompat.START))
  }

  @Test
  fun `drawer - sound library navigation item should be checked at start`() {
    assert(mainActivity.navigation_drawer.checkedItem?.itemId == R.id.library)
  }

  @Test
  fun `drawer - correct navigation item should be checked after pressing back`() {
    mainActivity.onNavigationItemSelected(RoboMenuItem(R.id.saved_presets))
    mainActivity.onNavigationItemSelected(RoboMenuItem(R.id.about))

    assert(mainActivity.navigation_drawer.checkedItem?.itemId == R.id.about)
    mainActivity.onBackPressed()
    assert(mainActivity.navigation_drawer.checkedItem?.itemId == R.id.saved_presets)
    mainActivity.onBackPressed()
    assert(mainActivity.navigation_drawer.checkedItem?.itemId == R.id.library)
  }

  @Test
  fun `navigation - should exit on pressing back button after launch`() {
    mainActivity.onBackPressed()
    assert(mainActivity.isFinishing)
  }

  @Test
  fun `navigation - sound library should be visible at start`() {
    assert(
      mainActivity
        .supportFragmentManager
        .getBackStackEntryAt(mainActivity.supportFragmentManager.backStackEntryCount - 1)
        .name
        == SoundLibraryFragment::class.java.simpleName
    )
  }

  @Test
  fun `navigation - about should be visible after clicking about in drawer`() {
    mainActivity.onNavigationItemSelected(RoboMenuItem(R.id.about))
    assert(
      mainActivity
        .supportFragmentManager
        .popBackStackImmediate(
          AboutFragment::class.java.simpleName,
          FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
    )
  }

  @Test
  fun `navigation - preset list should be visible after clicking saved presets in drawer`() {
    mainActivity.onNavigationItemSelected(RoboMenuItem(R.id.saved_presets))
    assert(
      mainActivity
        .supportFragmentManager
        .popBackStackImmediate(
          PresetFragment::class.java.simpleName,
          FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
    )
  }

  @Test
  fun `navigation - about should not be in the stack if returned to sound library`() {
    mainActivity.onNavigationItemSelected(RoboMenuItem(R.id.about))
    mainActivity.onNavigationItemSelected(RoboMenuItem(R.id.library))

    assert(
      !mainActivity
        .supportFragmentManager
        .popBackStackImmediate(
          AboutFragment::class.java.simpleName,
          FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
    )
  }

  @Test
  fun `navigation - sound library should be visible on pressing back at about`() {
    mainActivity.onNavigationItemSelected(RoboMenuItem(R.id.about))
    mainActivity.onBackPressed()

    assert(
      mainActivity
        .supportFragmentManager
        .getBackStackEntryAt(mainActivity.supportFragmentManager.backStackEntryCount - 1)
        .name
        == SoundLibraryFragment::class.java.simpleName
    )
  }

  @Test
  fun `navigation - should send correct intent on clicking report issues`() {
    val expectedIntent = Intent(
      Intent.ACTION_VIEW,
      Uri.parse(mainActivity.getString(R.string.app_issues_url))
    )

    mainActivity.onNavigationItemSelected(RoboMenuItem(R.id.report_issue))
    assert(expectedIntent.filterEquals(shadowOf(mainActivity).peekNextStartedActivity()))
  }

  @Test
  fun `navigation - should send correct intent on clicking rate on play store`() {
    val expectedIntent = Intent(
      Intent.ACTION_VIEW,
      Uri.parse("market://details?id=${mainActivity.packageName}")
    )

    mainActivity.onNavigationItemSelected(RoboMenuItem(R.id.rate_on_play_store))
    assert(expectedIntent.filterEquals(shadowOf(mainActivity).peekNextStartedActivity()))
  }

  @Test
  fun `should not add a new instance for visible fragment on clicking its navigation item`() {
    mainActivity.onNavigationItemSelected(RoboMenuItem(R.id.about))
    assert(mainActivity.supportFragmentManager.backStackEntryCount == 2)
    mainActivity.onNavigationItemSelected(RoboMenuItem(R.id.about))
    assert(mainActivity.supportFragmentManager.backStackEntryCount == 2)
  }
}

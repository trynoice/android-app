package com.github.ashutoshgngwr.noice

import androidx.core.view.GravityCompat
import androidx.fragment.app.FragmentManager
import com.github.ashutoshgngwr.noice.fragment.AboutFragment
import com.github.ashutoshgngwr.noice.fragment.HomeFragment
import kotlinx.android.synthetic.main.activity_main.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
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
  fun `drawer - home navigation item should be checked at start`() {
    assert(mainActivity.navigation_drawer.checkedItem?.itemId == R.id.home)
  }

  @Test
  fun `navigation - should exit on pressing back button after launch`() {
    mainActivity.onBackPressed()
    assert(mainActivity.isFinishing)
  }

  @Test
  fun `navigation - home should be visible at start`() {
    assert(
      mainActivity
        .supportFragmentManager
        .popBackStackImmediate(
          HomeFragment::class.java.simpleName,
          FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
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
  fun `navigation - about should not be in the stack if returned to home`() {
    mainActivity.onNavigationItemSelected(RoboMenuItem(R.id.about))
    mainActivity.onNavigationItemSelected(RoboMenuItem(R.id.home))

    assert(
      !mainActivity
        .supportFragmentManager
        .popBackStackImmediate(
          AboutFragment::class.java.simpleName,
          FragmentManager.POP_BACK_STACK_INCLUSIVE
        ) && mainActivity
        .supportFragmentManager
        .popBackStackImmediate(
          HomeFragment::class.java.simpleName,
          FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
    )
  }

  @Test
  fun `navigation - home should be visible on pressing back at about`() {
    mainActivity.onNavigationItemSelected(RoboMenuItem(R.id.about))
    mainActivity.onBackPressed()

    assert(
      !mainActivity
        .supportFragmentManager
        .popBackStackImmediate(
          AboutFragment::class.java.simpleName,
          FragmentManager.POP_BACK_STACK_INCLUSIVE
        ) && mainActivity
        .supportFragmentManager
        .popBackStackImmediate(
          HomeFragment::class.java.simpleName,
          FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
    )
  }
}

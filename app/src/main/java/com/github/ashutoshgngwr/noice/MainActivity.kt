package com.github.ashutoshgngwr.noice

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.fragment.AboutFragment
import com.github.ashutoshgngwr.noice.fragment.HomeFragment
import com.google.android.material.navigation.NavigationView
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

  private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

  private val homeFragment = HomeFragment()
  private val aboutFragment = AboutFragment()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    // setup toolbar to display animated drawer toggle button
    actionBarDrawerToggle = ActionBarDrawerToggle(this, layout_main, R.string.home, R.string.home)
    layout_main.addDrawerListener(actionBarDrawerToggle)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    actionBarDrawerToggle.syncState()

    // setup listener for navigation item clicks
    navigation_drawer.setNavigationItemSelectedListener(this)

    // bind navigation drawer menu items checked state with fragment back stack
    supportFragmentManager.addOnBackStackChangedListener {

      when (
        supportFragmentManager
          .getBackStackEntryAt(supportFragmentManager.backStackEntryCount - 1)
          .name
        ) {
        homeFragment.javaClass.simpleName -> navigation_drawer.setCheckedItem(R.id.home)
        aboutFragment.javaClass.simpleName -> navigation_drawer.setCheckedItem(R.id.about)
      }
    }

    // set home fragment when activity is created initially
    if (savedInstanceState == null) {
      setFragment(homeFragment)
    }
  }

  override fun onOptionsItemSelected(item: MenuItem?): Boolean {
    if (actionBarDrawerToggle.onOptionsItemSelected(item))
      return true
    return super.onOptionsItemSelected(item)
  }

  override fun onNavigationItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.home -> setFragment(homeFragment)
      R.id.about -> setFragment(aboutFragment)
    }

    layout_main.closeDrawer(GravityCompat.START)
    return true
  }

  override fun onBackPressed() {
    if (layout_main.isDrawerOpen(GravityCompat.START)) {
      layout_main.closeDrawer(GravityCompat.START)
    } else {
      // last fragment need not be removed, activity should be finished instead
      if (supportFragmentManager.backStackEntryCount > 1) {
        supportFragmentManager.popBackStackImmediate()
      } else {
        finish()
      }
    }
  }

  private fun setFragment(fragment: Fragment) {
    val tag = fragment.javaClass.simpleName

    // show fragment if it isn't present in back stack.
    // if it is present, pop back stack to bring it to front
    // this seems to be the only way to avoid duplicate fragments
    // in back stack without fighting the freaking framework
    if (
      !supportFragmentManager.popBackStackImmediate(tag, 0)
      && supportFragmentManager.findFragmentByTag(tag) == null
    ) {
      supportFragmentManager
        .beginTransaction()
        .setCustomAnimations(
          R.anim.enter_right,
          R.anim.exit_left,
          R.anim.enter_left,
          R.anim.exit_right
        )
        .replace(R.id.fragment_container, fragment)
        .addToBackStack(tag)
        .commit()
    }
  }
}

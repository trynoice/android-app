package com.github.ashutoshgngwr.noice.activity

import android.content.Intent
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.IdRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.InAppReviewFlowManager
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.cast.CastAPIWrapper
import com.github.ashutoshgngwr.noice.databinding.MainActivityBinding
import com.github.ashutoshgngwr.noice.fragment.AboutFragment
import com.github.ashutoshgngwr.noice.fragment.PresetFragment
import com.github.ashutoshgngwr.noice.fragment.RandomPresetFragment
import com.github.ashutoshgngwr.noice.fragment.SettingsFragment
import com.github.ashutoshgngwr.noice.fragment.SleepTimerFragment
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment
import com.github.ashutoshgngwr.noice.fragment.SupportDevelopmentFragment
import com.github.ashutoshgngwr.noice.fragment.WakeUpTimerFragment
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.google.android.material.navigation.NavigationView
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

  companion object {
    /**
     * [EXTRA_CURRENT_NAVIGATED_FRAGMENT] declares the key for intent extra value that is passed to
     * [MainActivity] for setting the given fragment to the top. The required value for this extra
     * should be an integer representing the menu item's id in the navigation view corresponding to
     * the fragment being requested.
     */
    const val EXTRA_CURRENT_NAVIGATED_FRAGMENT = "current_fragment"

    // maps fragments that have a one-to-one mapping with a menu item in the navigation drawer.
    // this map helps in reducing boilerplate for launching these fragments when appropriate
    // menu item is clicked in the navigation drawer.
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val NAVIGATED_FRAGMENTS = mapOf(
      R.id.library to SoundLibraryFragment::class.java,
      R.id.saved_presets to PresetFragment::class.java,
      R.id.sleep_timer to SleepTimerFragment::class.java,
      R.id.wake_up_timer to WakeUpTimerFragment::class.java,
      R.id.random_preset to RandomPresetFragment::class.java,
      R.id.settings to SettingsFragment::class.java,
      R.id.about to AboutFragment::class.java,
      R.id.support_development to SupportDevelopmentFragment::class.java
    )
  }

  private lateinit var binding: MainActivityBinding
  private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
  private lateinit var castAPIWrapper: CastAPIWrapper
  private lateinit var customTabsIntent: CustomTabsIntent
  private lateinit var settingsRepository: SettingsRepository

  private var playerManagerState = PlaybackStateCompat.STATE_STOPPED

  override fun onCreate(savedInstanceState: Bundle?) {
    // because cast context is lazy initialized, cast menu item wouldn't show up until
    // re-resuming the activity. adding this to prevent that.
    // This should implicitly init CastContext.
    castAPIWrapper = CastAPIWrapper.from(this, false)
    super.onCreate(savedInstanceState)

    settingsRepository = SettingsRepository.newInstance(this)
    AppCompatDelegate.setDefaultNightMode(settingsRepository.getAppThemeAsNightMode())

    binding = MainActivityBinding.inflate(layoutInflater)
    setContentView(binding.root)

    // setup toolbar to display animated drawer toggle button
    actionBarDrawerToggle = ActionBarDrawerToggle(
      this,
      binding.layoutMain,
      R.string.open_drawer,
      R.string.close_drawer
    )
    binding.layoutMain.addDrawerListener(actionBarDrawerToggle)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    actionBarDrawerToggle.syncState()

    // setup listener for navigation item clicks
    binding.navigationDrawer.setNavigationItemSelectedListener(this)

    // bind navigation drawer menu items checked state with fragment back stack
    supportFragmentManager.addOnBackStackChangedListener {
      val index = supportFragmentManager.backStackEntryCount - 1
      val currentFragmentName = supportFragmentManager.getBackStackEntryAt(index).name
      for ((id, fragment) in NAVIGATED_FRAGMENTS) {
        if (fragment.simpleName == currentFragmentName) {
          binding.navigationDrawer.setCheckedItem(id)
          break
        }
      }

      if (index == 0) {
        supportActionBar?.setTitle(R.string.app_name)
      } else {
        supportActionBar?.title = binding.navigationDrawer.checkedItem?.title
      }
    }

    // set sound library fragment when activity is created initially (screen-orientation change
    // will recall onCreate which will cause weird and unexpected fragment changes otherwise).
    if (supportFragmentManager.backStackEntryCount < 1) {
      var defaultFragmentID = R.id.library
      if (settingsRepository.shouldDisplaySavedPresetsAsHomeScreen()) {
        defaultFragmentID = R.id.saved_presets
      }

      setFragment(defaultFragmentID) // default fragment must be in the back stack
      setNavigatedFragment()
      AppIntroActivity.maybeStart(this) // show app intro if user hasn't already seen it
    }

    InAppReviewFlowManager.init(this)
    customTabsIntent = CustomTabsIntent.Builder()
      .setDefaultColorSchemeParams(
        CustomTabColorSchemeParams.Builder()
          .setToolbarColor(ResourcesCompat.getColor(resources, R.color.action_bar, theme))
          .build()
      )
      .build()
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    setIntent(intent)
    setNavigatedFragment()
  }

  private fun setNavigatedFragment() {
    intent?.also {
      if (it.hasExtra(EXTRA_CURRENT_NAVIGATED_FRAGMENT)) {
        setFragment(it.getIntExtra(EXTRA_CURRENT_NAVIGATED_FRAGMENT, 0))
      } else if (Intent.ACTION_APPLICATION_PREFERENCES == it.action) {
        setFragment(R.id.settings)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    EventBus.getDefault().register(this)
  }

  override fun onPause() {
    EventBus.getDefault().unregister(this)
    super.onPause()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    super.onCreateOptionsMenu(menu)
    castAPIWrapper.setUpMenuItem(menu, R.string.cast_media)
    menu.add(0, R.id.action_play_pause_toggle, 0, R.string.play_pause).also {
      it.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
      it.isVisible = PlaybackStateCompat.STATE_STOPPED != playerManagerState
      if (PlaybackStateCompat.STATE_PLAYING == playerManagerState) {
        it.setIcon(R.drawable.ic_action_play_to_pause)
      } else {
        it.setIcon(R.drawable.ic_action_pause_to_play)
      }

      (it.icon as Animatable).start()
      it.setOnMenuItemClickListener {
        if (PlaybackStateCompat.STATE_PLAYING == playerManagerState) {
          PlaybackController.pause(this)
        } else {
          PlaybackController.resume(this)
        }

        true
      }
    }

    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (actionBarDrawerToggle.onOptionsItemSelected(item))
      return true
    return super.onOptionsItemSelected(item)
  }

  override fun onNavigationItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      in NAVIGATED_FRAGMENTS -> setFragment(item.itemId)
      R.id.help -> {
        startActivity(Intent(this, AppIntroActivity::class.java))
      }
      R.id.report_issue -> {
        var url = getString(R.string.app_issues_github_url)
        if (BuildConfig.IS_PLAY_STORE_BUILD) {
          url = getString(R.string.app_issues_form_url)
        }

        customTabsIntent.launchUrl(this, Uri.parse(url))
      }
      R.id.submit_feedback -> {
        customTabsIntent.launchUrl(this, Uri.parse(getString(R.string.feedback_form_url)))
      }
    }

    // hack to avoid stuttering in animations
    binding.layoutMain.postDelayed({ binding.layoutMain.closeDrawer(GravityCompat.START) }, 150)
    return true
  }

  override fun onBackPressed() {
    if (binding.layoutMain.isDrawerOpen(GravityCompat.START)) {
      binding.layoutMain.closeDrawer(GravityCompat.START)
    } else {
      // last fragment need not be removed, activity should be finished instead
      if (supportFragmentManager.backStackEntryCount > 1) {
        supportFragmentManager.popBackStackImmediate()
      } else {
        finish()
      }
    }
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onPlayerManagerUpdate(event: MediaPlayerService.PlaybackUpdateEvent) {
    if (playerManagerState == event.state) {
      return
    }

    playerManagerState = event.state
    invalidateOptionsMenu()
  }

  private fun setFragment(@IdRes navItemID: Int) {
    val fragmentClass = NAVIGATED_FRAGMENTS[navItemID] ?: return
    val tag = fragmentClass.simpleName

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
        .replace(R.id.fragment_container, fragmentClass.newInstance(), tag)
        .addToBackStack(tag)
        .commit()
    }
  }
}

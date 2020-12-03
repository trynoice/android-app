package com.github.ashutoshgngwr.noice

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.IdRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.cast.CastAPIWrapper
import com.github.ashutoshgngwr.noice.databinding.MainActivityBinding
import com.github.ashutoshgngwr.noice.fragment.AboutFragment
import com.github.ashutoshgngwr.noice.fragment.DialogFragment
import com.github.ashutoshgngwr.noice.fragment.PresetFragment
import com.github.ashutoshgngwr.noice.fragment.SleepTimerFragment
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment
import com.github.ashutoshgngwr.noice.fragment.SupportDevelopmentFragment
import com.github.ashutoshgngwr.noice.fragment.WakeUpTimerFragment
import com.github.ashutoshgngwr.noice.sound.player.PlayerManager
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

    private const val PREF_APP_THEME = "app_theme"
    private const val APP_THEME_LIGHT = 0
    private const val APP_THEME_DARK = 1
    private const val APP_THEME_SYSTEM_DEFAULT = 2

    // maps fragments that have a one-to-one mapping with a menu item in the navigation drawer.
    // this map helps in reducing boilerplate for launching these fragments when appropriate
    // menu item is clicked in the navigation drawer.
    private val NAVIGATED_FRAGMENTS = mapOf(
      R.id.library to SoundLibraryFragment::class.java,
      R.id.saved_presets to PresetFragment::class.java,
      R.id.sleep_timer to SleepTimerFragment::class.java,
      R.id.wake_up_timer to WakeUpTimerFragment::class.java,
      R.id.about to AboutFragment::class.java,
      R.id.support_development to SupportDevelopmentFragment::class.java
    )
  }

  private lateinit var binding: MainActivityBinding
  private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle
  private lateinit var castAPIWrapper: CastAPIWrapper
  private var playerManagerState = PlayerManager.State.STOPPED

  override fun onCreate(savedInstanceState: Bundle?) {
    // because cast context is lazy initialized, cast menu item wouldn't show up until
    // re-resuming the activity. adding this to prevent that.
    // This should implicitly init CastContext.
    castAPIWrapper = CastAPIWrapper.from(this, false)
    super.onCreate(savedInstanceState)
    AppCompatDelegate.setDefaultNightMode(getNightModeFromPrefs())

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
    binding.navigationDrawer.menu.findItem(R.id.rate_on_play_store).isVisible =
      BuildConfig.IS_PLAY_STORE_BUILD

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

      if (R.id.library == binding.navigationDrawer.checkedItem?.itemId) {
        supportActionBar?.setTitle(R.string.app_name)
      } else {
        supportActionBar?.title = binding.navigationDrawer.checkedItem?.title
      }
    }

    // set sound library fragment when activity is created initially (screen-orientation change
    // will recall onCreate which will cause weird and unexpected fragment changes otherwise).
    if (savedInstanceState == null) {
      setFragment(intent.getIntExtra(EXTRA_CURRENT_NAVIGATED_FRAGMENT, R.id.library))

      // show app intro if user hasn't already seen it
      AppIntroActivity.maybeStart(this)
    }

    InAppReviewFlowManager.init(this)
  }

  override fun onResume() {
    super.onResume()
    EventBus.getDefault().register(this)
    val serviceIntent = Intent(this, MediaPlayerService::class.java)

    // start the media player service
    // workaround for Android 9+. See https://github.com/ashutoshgngwr/noice/issues/179
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).also {
        val importance = it.runningAppProcesses.firstOrNull()?.importance
          ?: ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE

        if (importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
          startService(serviceIntent)
        }
      }
    } else {
      startService(serviceIntent)
    }
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
      it.isVisible = PlayerManager.State.STOPPED != playerManagerState
      if (PlayerManager.State.PLAYING == playerManagerState) {
        it.setIcon(R.drawable.ic_action_play_to_pause)
      } else {
        it.setIcon(R.drawable.ic_action_pause_to_play)
      }

      (it.icon as Animatable).start()
      it.setOnMenuItemClickListener {
        val event: Any = if (PlayerManager.State.PLAYING == playerManagerState) {
          MediaPlayerService.PausePlaybackEvent()
        } else {
          MediaPlayerService.ResumePlaybackEvent()
        }

        EventBus.getDefault().post(event)
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
      R.id.app_theme -> {
        DialogFragment.show(supportFragmentManager) {
          title(R.string.app_theme)
          singleChoiceItems(
            items = resources.getStringArray(R.array.app_themes),
            currentChoice = getAppTheme(),
            onItemSelected = { setAppTheme(it) }
          )
        }
      }
      R.id.help -> {
        startActivity(Intent(this, AppIntroActivity::class.java))
      }
      R.id.report_issue -> {
        startActivity(
          Intent(
            Intent.ACTION_VIEW,
            Uri.parse(getString(R.string.app_issues_url))
          )
        )
      }
      R.id.rate_on_play_store -> {
        startActivity(
          Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.rate_us_on_play_store_url)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
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
  fun onPlayerManagerUpdate(event: MediaPlayerService.OnPlayerManagerUpdateEvent) {
    if (playerManagerState == event.state) {
      return
    }

    playerManagerState = event.state
    invalidateOptionsMenu()
  }

  /**
   * Gets user setting for app theme and converts it into its corresponding value from
   * array ([AppCompatDelegate.MODE_NIGHT_NO], [AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM],
   * [AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM]).
   */
  private fun getNightModeFromPrefs(): Int {
    return when (getAppTheme()) {
      APP_THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
      APP_THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
      APP_THEME_SYSTEM_DEFAULT -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
      else -> AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
    }
  }

  /**
   * Gets user setting for current app theme.
   * Returns one of [APP_THEME_LIGHT], [APP_THEME_DARK] or [APP_THEME_SYSTEM_DEFAULT]
   */
  private fun getAppTheme(): Int {
    return PreferenceManager.getDefaultSharedPreferences(this)
      .getInt(PREF_APP_THEME, APP_THEME_SYSTEM_DEFAULT)
  }

  /**
   * Sets user setting for current app theme.
   * @param newTheme should be one of [APP_THEME_LIGHT], [APP_THEME_DARK] or [APP_THEME_SYSTEM_DEFAULT]
   */
  private fun setAppTheme(newTheme: Int) {
    PreferenceManager.getDefaultSharedPreferences(this).edit {
      putInt(PREF_APP_THEME, newTheme)
    }

    recreate()
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

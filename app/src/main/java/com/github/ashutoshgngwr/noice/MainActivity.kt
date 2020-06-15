package com.github.ashutoshgngwr.noice

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.github.ashutoshgngwr.noice.cast.CastAPIWrapper
import com.github.ashutoshgngwr.noice.fragment.*
import com.github.ashutoshgngwr.noice.sound.player.PlayerManager
import com.google.android.material.navigation.NavigationView
import kotlinx.android.synthetic.main.activity_main.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

  companion object {
    private const val TAG = "MainActivity"
    private const val PREF_APP_THEME = "app_theme"
    private const val APP_THEME_LIGHT = 0
    private const val APP_THEME_DARK = 1
    private const val APP_THEME_SYSTEM_DEFAULT = 2
  }

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

    setContentView(R.layout.activity_main)

    // setup toolbar to display animated drawer toggle button
    actionBarDrawerToggle = ActionBarDrawerToggle(
      this,
      layout_main,
      R.string.open_drawer,
      R.string.close_drawer
    )
    layout_main.addDrawerListener(actionBarDrawerToggle)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    actionBarDrawerToggle.syncState()

    // setup listener for navigation item clicks
    navigation_drawer.setNavigationItemSelectedListener(this)
    navigation_drawer.menu.findItem(R.id.rate_on_play_store).isVisible =
      BuildConfig.IS_PLAY_STORE_BUILD

    // bind navigation drawer menu items checked state with fragment back stack
    supportFragmentManager.addOnBackStackChangedListener {
      when (
        supportFragmentManager
          .getBackStackEntryAt(supportFragmentManager.backStackEntryCount - 1)
          .name
        ) {
        SoundLibraryFragment::class.java.simpleName -> {
          navigation_drawer.setCheckedItem(R.id.library)
        }
        PresetFragment::class.java.simpleName -> {
          navigation_drawer.setCheckedItem(R.id.saved_presets)
        }
        SleepTimerFragment::class.java.simpleName -> {
          navigation_drawer.setCheckedItem(R.id.sleep_timer)
        }
        AboutFragment::class.java.simpleName -> {
          navigation_drawer.setCheckedItem(R.id.about)
        }
      }
    }

    // set sound library fragment when activity is created initially
    if (savedInstanceState == null) {
      setFragment(SoundLibraryFragment::class.java)
    }
  }

  override fun onResume() {
    super.onResume()
    EventBus.getDefault().register(this)

    // start the media player service
    // workaround for Android 9+. See https://github.com/ashutoshgngwr/noice/issues/179
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).also {
        val importance = it.runningAppProcesses.firstOrNull()?.importance
          ?: ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE

        if (importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
          startService(Intent(this, MediaPlayerService::class.java))
        }
      }
    } else {
      startService(Intent(this, MediaPlayerService::class.java))
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
      R.id.library -> {
        setFragment(SoundLibraryFragment::class.java)
      }
      R.id.saved_presets -> {
        setFragment(PresetFragment::class.java)
      }
      R.id.sleep_timer -> {
        setFragment(SleepTimerFragment::class.java)
      }
      R.id.app_theme -> {
        DialogFragment().show(supportFragmentManager) {
          title(R.string.app_theme)
          singleChoiceItems(
            itemsRes = R.array.app_themes,
            currentChoice = getAppTheme(),
            onItemSelected = { setAppTheme(it) }
          )
        }
      }
      R.id.about -> {
        setFragment(AboutFragment::class.java)
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
        try {
          startActivity(
            Intent(
              Intent.ACTION_VIEW,
              Uri.parse("market://details?id=$packageName")
            ).addFlags(
              Intent.FLAG_ACTIVITY_NO_HISTORY
                or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            )
          )
        } catch (e: ActivityNotFoundException) {
          Log.i(TAG, "Play store is not installed on the device", e)
        }
      }
    }

    // hack to avoid stuttering in animations
    layout_main.postDelayed({ layout_main.closeDrawer(GravityCompat.START) }, 150)
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
    PreferenceManager.getDefaultSharedPreferences(this)
      .edit()
      .putInt(PREF_APP_THEME, newTheme)
      .apply()

    recreate()
  }

  private fun <T : Fragment> setFragment(fragmentClass: Class<T>) {
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

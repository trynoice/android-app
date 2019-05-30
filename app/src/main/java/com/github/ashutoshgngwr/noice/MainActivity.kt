package com.github.ashutoshgngwr.noice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Bundle
import android.os.IBinder
import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import com.github.ashutoshgngwr.noice.fragment.AboutFragment
import com.github.ashutoshgngwr.noice.fragment.SoundLibraryFragment
import com.google.android.material.navigation.NavigationView
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

  var mSoundManager: SoundManager? = null

  private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle

  private val soundLibraryFragment = SoundLibraryFragment()
  private val aboutFragment = AboutFragment()

  private val mServiceConnection = object : ServiceConnection {
    override fun onServiceDisconnected(name: ComponentName?) {
      mSoundManager?.setOnPlaybackStateChangeListener(null)
      mSoundManager = null
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
      mSoundManager = (service as MediaPlayerService.PlaybackBinder).getSoundManager()
      mSoundManager?.setOnPlaybackStateChangeListener(
        object : SoundManager.OnPlaybackStateChangeListener {
          override fun onPlaybackStateChanged() {
            soundLibraryFragment.recyclerView.adapter?.notifyDataSetChanged()
          }
        }
      )

      // once service is connected, update playback state in UI
      soundLibraryFragment.recyclerView.adapter?.notifyDataSetChanged()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
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

    // bind navigation drawer menu items checked state with fragment back stack
    supportFragmentManager.addOnBackStackChangedListener {
      when (
        supportFragmentManager
          .getBackStackEntryAt(supportFragmentManager.backStackEntryCount - 1)
          .name
        ) {
        soundLibraryFragment.javaClass.simpleName ->
          navigation_drawer.setCheckedItem(R.id.library)

        aboutFragment.javaClass.simpleName ->
          navigation_drawer.setCheckedItem(R.id.about)
      }
    }

    // set sound library fragment when activity is created initially
    if (savedInstanceState == null) {
      setFragment(soundLibraryFragment)
    }

    // volume control to type "media"
    volumeControlStream = AudioManager.STREAM_MUSIC
  }

  override fun onStart() {
    super.onStart()
    bindService(
      Intent(this, MediaPlayerService::class.java),
      mServiceConnection,
      Context.BIND_AUTO_CREATE
    )
  }

  override fun onStop() {
    super.onStop()
    unbindService(mServiceConnection)
  }

  override fun onOptionsItemSelected(item: MenuItem?): Boolean {
    if (actionBarDrawerToggle.onOptionsItemSelected(item))
      return true
    return super.onOptionsItemSelected(item)
  }

  override fun onNavigationItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.library -> setFragment(soundLibraryFragment)
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

package com.github.ashutoshgngwr.noice.fragment

import android.net.Uri
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.HomeFragmentBinding
import com.github.ashutoshgngwr.noice.ext.launchInCustomTab
import com.github.ashutoshgngwr.noice.navigation.Navigable
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode


class HomeFragment : Fragment(), Navigable {

  private lateinit var binding: HomeFragmentBinding
  private lateinit var navController: NavController
  private lateinit var childNavController: NavController
  private lateinit var app: NoiceApplication

  private var playerManagerState = PlaybackStateCompat.STATE_STOPPED

  private val childNavDestChangeListener = { _: NavController, _: NavDestination, _: Bundle? ->
    activity?.invalidateOptionsMenu() ?: Unit
  }

  // Do not refresh user preference when reconstructing this fragment from a previously saved state.
  // For whatever reasons, it makes the bottom navigation view go out of sync.
  private val shouldDisplayPresetsAsHomeScreen by lazy {
    SettingsRepository.newInstance(requireContext())
      .shouldDisplayPresetsAsHomeScreen()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)

    app = NoiceApplication.of(requireContext())
    EventBus.getDefault().register(this)
  }

  override fun onDestroy() {
    EventBus.getDefault().unregister(this)
    super.onDestroy()
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = HomeFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, state: Bundle?) {
    navController = view.findNavController()
    val navHostFragment = requireNotNull(binding.navHostFragment.getFragment<NavHostFragment>())
    childNavController = navHostFragment.navController

    if (shouldDisplayPresetsAsHomeScreen) {
      childNavController.navigate(
        R.id.presets, null, NavOptions.Builder()
          .setPopUpTo(R.id.library, true)
          .build()
      )
    }

    binding.bottomNav.setupWithNavController(childNavController)
    childNavController.addOnDestinationChangedListener(childNavDestChangeListener)
  }

  override fun onDestroyView() {
    childNavController.removeOnDestinationChangedListener(childNavDestChangeListener)
    super.onDestroyView()
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    app.castAPIProvider.addMenuItem(requireContext(), menu, R.string.cast_media)
    val displayPlaybackControls = childNavController.currentDestination?.id != R.id.wake_up_timer
    if (displayPlaybackControls && PlaybackStateCompat.STATE_STOPPED != playerManagerState) {
      addPlaybackToggleMenuItem(menu)
    }

    inflater.inflate(R.menu.home_menu, menu)
    menu.findItem(R.id.sleep_timer)?.isVisible = displayPlaybackControls
    super.onCreateOptionsMenu(menu, inflater)
  }

  private fun addPlaybackToggleMenuItem(menu: Menu): MenuItem {
    return menu.add(0, R.id.action_playback_toggle, 0, R.string.play_pause).apply {
      setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
      if (PlaybackStateCompat.STATE_PLAYING == playerManagerState) {
        setIcon(R.drawable.ic_pause_24dp)
      } else {
        setIcon(R.drawable.ic_play_arrow_24dp)
      }

      setOnMenuItemClickListener {
        if (PlaybackStateCompat.STATE_PLAYING == playerManagerState) {
          PlaybackController.pause(requireContext())
        } else {
          PlaybackController.resume(requireContext())
        }

        app.analyticsProvider.logEvent("playback_toggle_click", bundleOf())
        true
      }
    }
  }

  override fun onNavDestinationSelected(@IdRes destID: Int): Boolean {
    return binding.bottomNav.menu.findItem(destID)?.let {
      NavigationUI.onNavDestinationSelected(it, childNavController)
    } ?: false
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (NavigationUI.onNavDestinationSelected(item, childNavController)) {
      return true
    }

    if (NavigationUI.onNavDestinationSelected(item, navController)) {
      return true
    }

    when (item.itemId) {
      R.id.report_issue -> {
        var url = getString(R.string.app_issues_github_url)
        if (BuildConfig.IS_PLAY_STORE_BUILD) {
          url = getString(R.string.app_issues_form_url)
        }

        Uri.parse(url).launchInCustomTab(requireContext())
        app.analyticsProvider.logEvent("issue_tracker_open", bundleOf())
      }

      R.id.submit_feedback -> {
        Uri.parse(getString(R.string.feedback_form_url)).launchInCustomTab(requireContext())
        app.analyticsProvider.logEvent("feedback_form_open", bundleOf())
      }

      else -> return super.onOptionsItemSelected(item)
    }

    return true
  }

  @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
  fun onPlayerManagerUpdate(event: MediaPlayerService.PlaybackUpdateEvent) {
    if (playerManagerState == event.state) {
      return
    }

    playerManagerState = event.state
    activity?.invalidateOptionsMenu()
  }
}

package com.github.ashutoshgngwr.noice.fragment

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
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.HomeFragmentBinding
import com.github.ashutoshgngwr.noice.navigation.Navigable
import com.github.ashutoshgngwr.noice.playback.PlaybackController
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.CastApiProvider
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import javax.inject.Inject


@AndroidEntryPoint
class HomeFragment : Fragment(), Navigable {

  private lateinit var binding: HomeFragmentBinding
  private var playerManagerState = PlaybackStateCompat.STATE_STOPPED

  private val homeNavController: NavController by lazy {
    val navHostFragment = binding.homeNavHostFragment.getFragment<NavHostFragment>()
    requireNotNull(navHostFragment) { "failed to get the home NavHostFragment from the view tree" }
    navHostFragment.navController
  }

  private val homeNavGraph: NavGraph by lazy {
    homeNavController.navInflater.inflate(R.navigation.home)
  }

  @set:Inject
  internal lateinit var eventBus: EventBus

  @set:Inject
  internal lateinit var settingsRepository: SettingsRepository

  @set:Inject
  internal lateinit var castApiProvider: CastApiProvider

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  @set:Inject
  internal lateinit var playbackController: PlaybackController

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setHasOptionsMenu(true)
    eventBus.register(this)
  }

  override fun onDestroy() {
    eventBus.unregister(this)
    super.onDestroy()
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = HomeFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, state: Bundle?) {
    if (settingsRepository.shouldDisplayPresetsAsHomeScreen()) {
      homeNavGraph.setStartDestination(R.id.presets)
    }

    homeNavController.graph = homeNavGraph
    binding.bottomNav.setupWithNavController(homeNavController)
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    castApiProvider.addMenuItem(requireContext(), menu, R.string.cast_media)
    val displayPlaybackControls = homeNavController.currentDestination?.id != R.id.wake_up_timer
      && PlaybackStateCompat.STATE_STOPPED != playerManagerState

    if (displayPlaybackControls) {
      addPlaybackToggleMenuItem(menu)
    }

    super.onCreateOptionsMenu(menu, inflater)
  }

  private fun addPlaybackToggleMenuItem(menu: Menu): MenuItem {
    return if (PlaybackStateCompat.STATE_PLAYING == playerManagerState) {
      menu.add(0, R.id.action_pause, 0, R.string.pause).apply {
        setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        setIcon(R.drawable.ic_pause_24dp)
        setOnMenuItemClickListener {
          playbackController.pause()
          analyticsProvider.logEvent("playback_toggle_click", bundleOf())
          true
        }
      }
    } else {
      menu.add(0, R.id.action_resume, 0, R.string.play).apply {
        setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        setIcon(R.drawable.ic_play_arrow_24dp)
        setOnMenuItemClickListener {
          playbackController.resume()
          analyticsProvider.logEvent("playback_toggle_click", bundleOf())
          true
        }
      }
    }
  }

  override fun onNavDestinationSelected(@IdRes destID: Int): Boolean {
    return binding.bottomNav.menu.findItem(destID)?.let {
      NavigationUI.onNavDestinationSelected(it, homeNavController)
    } ?: false
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

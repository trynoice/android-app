package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.navArgs
import androidx.navigation.ui.setupWithNavController
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.HomeFragmentBinding
import com.github.ashutoshgngwr.noice.engine.PlaybackController
import com.github.ashutoshgngwr.noice.engine.PlaybackState
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.CastApiProvider
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class HomeFragment : Fragment(), MenuProvider, NavController.OnDestinationChangedListener {

  private lateinit var binding: HomeFragmentBinding
  private var playerManagerState = PlaybackState.STOPPED

  private val navArgs: HomeFragmentArgs by navArgs()
  private val homeNavController: NavController by lazy {
    binding.homeNavHostFragment.getFragment<NavHostFragment>().navController
  }

  private val homeNavGraph: NavGraph by lazy {
    homeNavController.navInflater.inflate(R.navigation.home)
  }

  @set:Inject
  internal lateinit var settingsRepository: SettingsRepository

  @set:Inject
  internal lateinit var castApiProvider: CastApiProvider

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  @set:Inject
  internal lateinit var playbackController: PlaybackController

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = HomeFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, state: Bundle?) {
    requireActivity().addMenuProvider(this, viewLifecycleOwner)
    if (settingsRepository.shouldDisplayPresetsAsHomeScreen()) {
      homeNavGraph.setStartDestination(R.id.presets)
    }

    homeNavController.addOnDestinationChangedListener(this)
    homeNavController.graph = homeNavGraph
    binding.bottomNav.setupWithNavController(homeNavController)
    if (navArgs.navDestination != ResourcesCompat.ID_NULL) {
      homeNavController.navigate(navArgs.navDestination, navArgs.navDestinationArgs)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      playbackController.getPlayerManagerState()
        .collect { state ->
          playerManagerState = state
          activity?.invalidateOptionsMenu()
        }
    }
  }

  override fun onDestroyView() {
    homeNavController.removeOnDestinationChangedListener(this)
    super.onDestroyView()
  }

  override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
    castApiProvider.addMenuItem(requireContext(), menu, R.string.cast_media)
    val displayPlaybackControls = homeNavController.currentDestination?.id != R.id.alarms
      && !playerManagerState.oneOf(PlaybackState.STOPPING, PlaybackState.STOPPED)

    if (displayPlaybackControls) {
      if (playerManagerState.oneOf(PlaybackState.PAUSED, PlaybackState.PAUSING)) {
        createResumeMenuItem(menu)
      } else {
        createPauseMenuItem(menu)
      }
    }
  }

  override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
    return false
  }

  private fun createPauseMenuItem(menu: Menu): MenuItem {
    return menu.add(0, R.id.action_pause, 0, R.string.pause)
      .apply { setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM) }
      .setIcon(R.drawable.ic_baseline_pause_24)
      .setOnMenuItemClickListener {
        playbackController.pause()
        analyticsProvider.logEvent("playback_toggle_click", bundleOf())
        true
      }
  }

  private fun createResumeMenuItem(menu: Menu): MenuItem {
    return menu.add(0, R.id.action_resume, 0, R.string.play)
      .apply { setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM) }
      .setIcon(R.drawable.ic_baseline_play_arrow_24)
      .setOnMenuItemClickListener {
        playbackController.resume()
        analyticsProvider.logEvent("playback_toggle_click", bundleOf())
        true
      }
  }

  override fun onDestinationChanged(
    controller: NavController,
    destination: NavDestination,
    arguments: Bundle?
  ) {
    destination.label?.also { label ->
      (activity as? AppCompatActivity)?.supportActionBar?.title = label
    }
  }
}

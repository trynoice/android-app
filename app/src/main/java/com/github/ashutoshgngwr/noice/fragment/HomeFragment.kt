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
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.navArgs
import androidx.navigation.navOptions
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.cast.CastApiProvider
import com.github.ashutoshgngwr.noice.databinding.HomeFragmentBinding
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


@AndroidEntryPoint
class HomeFragment : Fragment(), MenuProvider, NavController.OnDestinationChangedListener {

  private lateinit var binding: HomeFragmentBinding

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
      // navigating between bottom navigation view items using NavController directly is causing the
      // bottom navigation view to misbehave. Essentially, if fragment A is at the top and you
      // navigate to B directly using the nav controller, you can go back to A by pressing the back
      // button but not by selecting A from the bottom navigation view.
      binding.bottomNav.menu.findItem(navArgs.navDestination)?.let { item ->
        NavigationUI.onNavDestinationSelected(item, homeNavController)
      }

      homeNavController.navigate(navArgs.navDestination, navArgs.navDestinationArgs, navOptions {
        popUpTo(navArgs.navDestination) { inclusive = true }
      })
    }
  }

  override fun onDestroyView() {
    homeNavController.removeOnDestinationChangedListener(this)
    super.onDestroyView()
  }

  override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
    castApiProvider.addMenuItem(requireContext(), menu, R.string.cast_media)
  }

  override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
    return false
  }

  override fun onDestinationChanged(
    controller: NavController,
    destination: NavDestination,
    arguments: Bundle?,
  ) {
    destination.label?.also { label ->
      (activity as? AppCompatActivity)?.supportActionBar?.title = label
    }

    binding.playbackController.isVisible =
      homeNavController.currentDestination?.id != R.id.alarms
        && homeNavController.currentDestination?.id != R.id.account
  }
}

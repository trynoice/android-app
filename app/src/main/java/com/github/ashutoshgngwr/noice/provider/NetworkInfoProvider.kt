package com.github.ashutoshgngwr.noice.provider

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors device's internet connectivity and provides its live events as a
 * [StateFlow][offlineState] to the entire application.
 */
@Singleton
class NetworkInfoProvider @Inject constructor(
  @ApplicationContext context: Context,
) : ConnectivityManager.NetworkCallback() {

  private val connectivityManager: ConnectivityManager? by lazy { context.getSystemService() }
  private val hasInternet = hashMapOf<Network, Boolean>()
  private val _offlineState = MutableStateFlow(true)

  /**
   * A [StateFlow] that emits whenever device's connection state changes. Emits `true` when the
   * device network is offline and `false` when it comes back online.
   */
  val offlineState: StateFlow<Boolean> = _offlineState

  init {
    connectivityManager?.registerNetworkCallback(
      NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build(),
      this
    )
  }

  override fun onAvailable(network: Network) {
    hasInternet[network] = true
    notifyChanged()
  }

  override fun onLost(network: Network) {
    hasInternet[network] = false
    notifyChanged()
  }

  override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
    hasInternet[network] = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    notifyChanged()
  }

  private fun notifyChanged() {
    _offlineState.value = hasInternet.values.none { it }
  }
}

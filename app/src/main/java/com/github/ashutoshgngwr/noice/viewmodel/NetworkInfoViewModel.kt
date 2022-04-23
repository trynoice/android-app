package com.github.ashutoshgngwr.noice.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * A [ViewModel] that listens for network changes and exposes them as [Flow]s.
 */
@HiltViewModel
class NetworkInfoViewModel @Inject constructor(
  @ApplicationContext context: Context,
) : ViewModel() {

  private val connectivityManager: ConnectivityManager? by lazy { context.getSystemService() }
  private val hasInternet = hashMapOf<Network, Boolean>()
  private val _isOnline = MutableStateFlow(isConnectedToInternet())
  private val networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
      Log.i(LOG_TAG, "onAvailable: $network")
      hasInternet[network] = true
      notifyChanged()
    }

    override fun onLost(network: Network) {
      Log.i(LOG_TAG, "onLost: $network")
      hasInternet[network] = false
      notifyChanged()
    }

    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
      hasInternet[network] = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      Log.i(LOG_TAG, "onCapabilitiesChanged: $network internet=${hasInternet[network]}")
      notifyChanged()
    }

    private fun notifyChanged() {
      _isOnline.value = hasInternet.values.any { it }
    }
  }

  /**
   * A [StateFlow] that emits whenever device's connection state changes. Emits `true` when the
   * device's internet is online and `false` when it goes offline.
   */
  val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

  init {
    val networkRequest = NetworkRequest.Builder()
      .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      .build()

    connectivityManager?.registerNetworkCallback(networkRequest, networkCallback)
  }

  override fun onCleared() {
    connectivityManager?.unregisterNetworkCallback(networkCallback)
  }

  private fun isConnectedToInternet(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      connectivityManager?.getNetworkCapabilities(connectivityManager?.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

    } else {
      @Suppress("DEPRECATION")
      connectivityManager?.activeNetworkInfo?.isConnectedOrConnecting
    } ?: false
  }

  companion object {
    private const val LOG_TAG = "NetworkInfoViewModel"
  }
}

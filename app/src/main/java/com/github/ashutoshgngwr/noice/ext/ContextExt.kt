package com.github.ashutoshgngwr.noice.ext

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat
import com.github.ashutoshgngwr.noice.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Launches a [CustomTabsIntent] with default application theme ([R.style.Theme_App]) for the given
 * [uri].
 */
fun Context.startCustomTab(uri: String) {
  val tbColor = ResourcesCompat.getColor(resources, R.color.action_bar, theme)
  val colorParams = CustomTabColorSchemeParams.Builder()
    .setToolbarColor(tbColor)
    .setNavigationBarColor(tbColor)
    .build()

  val customTabsIntent = CustomTabsIntent.Builder()
    .setDefaultColorSchemeParams(colorParams)
    .build()

  customTabsIntent.launchUrl(this, Uri.parse(uri))
}

/**
 * Launches a [CustomTabsIntent] with default application theme ([R.style.Theme_App]) for the given
 * [uri string resource][uriStringRes].
 */
fun Context.startCustomTab(@StringRes uriStringRes: Int) {
  startCustomTab(getString(uriStringRes))
}

fun Context.getInternetConnectivityFlow(): Flow<Boolean> = callbackFlow {
  val connectivityManager = requireNotNull(getSystemService<ConnectivityManager>())
  trySend(
    // emit initial state
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

    } else {
      @Suppress("DEPRECATION")
      connectivityManager.activeNetworkInfo?.isConnectedOrConnecting
    } ?: false
  )

  // observe network changes
  val hasInternet = hashMapOf<Network, Boolean>()
  val networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
      hasInternet[network] = true
      notifyChanged()
    }

    override fun onLost(network: Network) {
      hasInternet.remove(network)
      notifyChanged()
    }

    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
      hasInternet[network] = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      notifyChanged()
    }

    private fun notifyChanged() {
      trySend(hasInternet.values.any { it })
    }
  }

  val networkRequest = NetworkRequest.Builder()
    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    .build()

  connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
  awaitClose { connectivityManager.unregisterNetworkCallback(networkCallback) }
}

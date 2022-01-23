package com.github.ashutoshgngwr.noice.ext

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController

/**
 * Automatically manages addition and removal of the [NavController.OnDestinationChangedListener]
 * based on the lifecycle state of the [owner].
 */
fun NavController.registerOnDestinationChangedListener(
  owner: LifecycleOwner,
  listener: NavController.OnDestinationChangedListener
) {
  owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {
      addOnDestinationChangedListener(listener)
    }

    override fun onDestroy(owner: LifecycleOwner) {
      removeOnDestinationChangedListener(listener)
      owner.lifecycle.removeObserver(this)
    }
  })
}

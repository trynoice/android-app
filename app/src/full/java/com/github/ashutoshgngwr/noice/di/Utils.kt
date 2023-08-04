package com.github.ashutoshgngwr.noice.di

import android.content.Context
import android.os.Build
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

fun isGoogleMobileServiceAvailable(context: Context): Boolean {
  return GoogleApiAvailability.getInstance()
    .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}

fun getInstallingPackageName(context: Context): String? {
  return try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      context.packageManager.getInstallSourceInfo(context.packageName)
        .installingPackageName
    } else {
      @Suppress("DEPRECATION")
      context.packageManager.getInstallerPackageName(context.packageName)
    }
  } catch (e: Throwable) {
    null
  }
}

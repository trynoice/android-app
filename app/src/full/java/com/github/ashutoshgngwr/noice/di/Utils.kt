package com.github.ashutoshgngwr.noice.di

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

fun isGoogleMobileServiceAvailable(context: Context): Boolean {
  return GoogleApiAvailability.getInstance()
    .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}

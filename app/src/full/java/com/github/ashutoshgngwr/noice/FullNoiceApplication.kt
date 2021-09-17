package com.github.ashutoshgngwr.noice

import com.github.ashutoshgngwr.noice.provider.InAppBillingDonateViewProvider
import com.github.ashutoshgngwr.noice.provider.PlaystoreReviewFlowProvider
import com.github.ashutoshgngwr.noice.provider.RealAnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.RealBillingProvider
import com.github.ashutoshgngwr.noice.provider.RealCastAPIProvider
import com.github.ashutoshgngwr.noice.provider.RealCrashlyticsProvider
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp


@Suppress("unused")
class FullNoiceApplication : NoiceApplication() {

  override fun initProviders() {
    super.initProviders()
    FirebaseApp.initializeApp(this)

    // crashlytics and analytics doesn't require Google Mobile Services to work.
    crashlyticsProvider = RealCrashlyticsProvider
    analyticsProvider = RealAnalyticsProvider

    if (isGoogleMobileServicesAvailable()) {
      castAPIProvider = RealCastAPIProvider(this)
      reviewFlowProvider = PlaystoreReviewFlowProvider
      billingProvider = RealBillingProvider
      donateViewProvider = InAppBillingDonateViewProvider
    }
  }

  override fun isGoogleMobileServicesAvailable(): Boolean {
    val result = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
    if (result != ConnectionResult.SUCCESS) {
      return super.isGoogleMobileServicesAvailable()
    }

    return true
  }
}

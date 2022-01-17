package com.github.ashutoshgngwr.noice

import android.content.Context
import android.util.Log
import androidx.annotation.CallSuper
import androidx.annotation.VisibleForTesting
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.BillingProvider
import com.github.ashutoshgngwr.noice.provider.CastAPIProvider
import com.github.ashutoshgngwr.noice.provider.CrashlyticsProvider
import com.github.ashutoshgngwr.noice.provider.DonateViewProvider
import com.github.ashutoshgngwr.noice.provider.DummyAnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.DummyBillingProvider
import com.github.ashutoshgngwr.noice.provider.DummyCastAPIProvider
import com.github.ashutoshgngwr.noice.provider.DummyCrashlyticsProvider
import com.github.ashutoshgngwr.noice.provider.GitHubReviewFlowProvider
import com.github.ashutoshgngwr.noice.provider.OpenCollectiveDonateViewProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.repository.SettingsRepository
import com.trynoice.api.client.NoiceApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

open class NoiceApplication : android.app.Application() {

  companion object {
    /**
     * Convenience method that returns [NoiceApplication] from the provided [context].
     */
    fun of(context: Context): NoiceApplication = context.applicationContext as NoiceApplication
  }

  lateinit var castAPIProvider: CastAPIProvider
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal set

  lateinit var reviewFlowProvider: ReviewFlowProvider
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal set

  lateinit var crashlyticsProvider: CrashlyticsProvider
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal set

  lateinit var analyticsProvider: AnalyticsProvider
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal set

  lateinit var billingProvider: BillingProvider
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal set

  lateinit var donateViewProvider: DonateViewProvider
    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    internal set

  override fun onCreate() {
    super.onCreate()
    initProviders()
    SettingsRepository.newInstance(this)
      .shouldShareUsageData()
      .also {
        analyticsProvider.setCollectionEnabled(it)
        crashlyticsProvider.setCollectionEnabled(it)
      }

    GlobalScope.launch(Dispatchers.IO) {
      Log.e(this::class.simpleName, "requesting available plan details")
      Log.e(this::class.simpleName, NoiceApiClient.subscriptions().getPlans().body().toString())
    }
  }

  /**
   * [initProviders] is invoked when application is created (in [onCreate]). It can be overridden by
   * a subclass to swap default implementations of [castAPIProvider], [reviewFlowProvider],
   * [crashlyticsProvider], [analyticsProvider], [billingProvider] and [donateViewProvider].
   */
  @CallSuper
  protected open fun initProviders() {
    castAPIProvider = DummyCastAPIProvider
    reviewFlowProvider = GitHubReviewFlowProvider
    crashlyticsProvider = DummyCrashlyticsProvider
    analyticsProvider = DummyAnalyticsProvider
    billingProvider = DummyBillingProvider
    donateViewProvider = OpenCollectiveDonateViewProvider
  }

  /**
   * Indicates if Google Mobile Services are available on the client device.
   */
  open fun isGoogleMobileServicesAvailable(): Boolean {
    return false
  }
}

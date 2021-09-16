package com.github.ashutoshgngwr.noice

import android.content.Context
import androidx.annotation.CallSuper
import androidx.annotation.VisibleForTesting
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.BillingProvider
import com.github.ashutoshgngwr.noice.provider.CastAPIProvider
import com.github.ashutoshgngwr.noice.provider.CrashlyticsProvider
import com.github.ashutoshgngwr.noice.provider.DummyAnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.DummyBillingProvider
import com.github.ashutoshgngwr.noice.provider.DummyCastAPIProvider
import com.github.ashutoshgngwr.noice.provider.DummyCrashlyticsProvider
import com.github.ashutoshgngwr.noice.provider.GitHubReviewFlowProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import com.github.ashutoshgngwr.noice.repository.SettingsRepository

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

  override fun onCreate() {
    super.onCreate()
    initProviders()
    SettingsRepository.newInstance(this)
      .shouldShareUsageData()
      .also {
        analyticsProvider.setCollectionEnabled(it)
        crashlyticsProvider.setCollectionEnabled(it)
      }
  }

  /**
   * [initProviders] is invoked when application is created (in [onCreate]). It can be overridden by
   * a subclass to swap default implementations of [castAPIProvider], [reviewFlowProvider],
   * [crashlyticsProvider], [analyticsProvider] and [billingProvider].
   */
  @CallSuper
  protected open fun initProviders() {
    castAPIProvider = DummyCastAPIProvider
    reviewFlowProvider = GitHubReviewFlowProvider
    crashlyticsProvider = DummyCrashlyticsProvider
    analyticsProvider = DummyAnalyticsProvider
    billingProvider = DummyBillingProvider
  }

  /**
   * Indicates if Google Mobile Services are available on the client device.
   */
  open fun isGoogleMobileServicesAvailable(): Boolean {
    return false
  }
}

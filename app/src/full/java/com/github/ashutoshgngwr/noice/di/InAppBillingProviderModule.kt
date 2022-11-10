package com.github.ashutoshgngwr.noice.di

import android.content.Context
import com.github.ashutoshgngwr.noice.AppDispatchers
import com.github.ashutoshgngwr.noice.provider.DummyInAppBillingProvider
import com.github.ashutoshgngwr.noice.provider.InAppBillingProvider
import com.github.ashutoshgngwr.noice.provider.RealInAppBillingProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.plus
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InAppBillingProviderModule {

  @Provides
  @Singleton
  fun inAppBillingProvider(
    @ApplicationContext context: Context,
    appDispatchers: AppDispatchers,
  ): InAppBillingProvider {
    if (isGoogleMobileServiceAvailable(context)) {
      return RealInAppBillingProvider(
        context,
        MainScope() + CoroutineName("InAppBillingProvider"),
        appDispatchers,
      )
    }

    return DummyInAppBillingProvider
  }
}

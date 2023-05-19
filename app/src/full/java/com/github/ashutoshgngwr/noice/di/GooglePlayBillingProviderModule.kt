package com.github.ashutoshgngwr.noice.di

import android.content.Context
import com.github.ashutoshgngwr.noice.AppDispatchers
import com.github.ashutoshgngwr.noice.billing.GooglePlayBillingProvider
import com.github.ashutoshgngwr.noice.data.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GooglePlayBillingProviderModule {

  @Provides
  @Singleton
  fun googlePlayBillingProvider(
    @ApplicationContext context: Context,
    appDb: AppDatabase,
    @AppCoroutineScope appScope: CoroutineScope,
    appDispatchers: AppDispatchers,
  ): GooglePlayBillingProvider? {
    val installer = getInstallingPackageName(context)
    if (installer == "com.android.vending" && isGoogleMobileServiceAvailable(context)) {
      return GooglePlayBillingProvider(
        context = context,
        appDb = appDb,
        defaultScope = appScope + CoroutineName("GooglePlayBillingProvider"),
        appDispatchers = appDispatchers,
      )
    }

    return null
  }
}

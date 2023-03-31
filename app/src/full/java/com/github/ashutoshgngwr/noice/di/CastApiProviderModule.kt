package com.github.ashutoshgngwr.noice.di

import android.content.Context
import com.github.ashutoshgngwr.noice.AppDispatchers
import com.github.ashutoshgngwr.noice.provider.CastApiProvider
import com.github.ashutoshgngwr.noice.provider.DummyCastApiProvider
import com.github.ashutoshgngwr.noice.provider.RealCastApiProvider
import com.google.gson.Gson
import com.trynoice.api.client.NoiceApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CastApiProviderModule {

  @Provides
  @Singleton
  fun castApiProvider(
    @ApplicationContext context: Context,
    @AppCoroutineScope appScope: CoroutineScope,
    appDispatchers: AppDispatchers,
    apiClient: NoiceApiClient,
    gson: Gson,
  ): CastApiProvider {
    if (isGoogleMobileServiceAvailable(context)) {
      return RealCastApiProvider(
        context = context,
        accessTokenGetter = { callback ->
          appScope.launch(appDispatchers.main) {
            val token = apiClient.getAccessToken()
            callback.invoke(token)
          }
        },
        gson = gson,
      )
    }

    return DummyCastApiProvider
  }
}

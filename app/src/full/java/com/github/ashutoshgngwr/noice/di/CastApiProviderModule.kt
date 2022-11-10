package com.github.ashutoshgngwr.noice.di

import android.content.Context
import com.github.ashutoshgngwr.noice.provider.CastApiProvider
import com.github.ashutoshgngwr.noice.provider.DummyCastApiProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CastApiProviderModule {

  @Provides
  @Singleton
  fun castApiProvider(@ApplicationContext context: Context): CastApiProvider {
    // TODO: implement cast
//    if (isGoogleMobileServiceAvailable(context)) {
//      return RealCastApiProvider(context)
//    }

    return DummyCastApiProvider
  }
}

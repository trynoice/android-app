package com.github.ashutoshgngwr.noice.di

import com.github.ashutoshgngwr.noice.metrics.CrashlyticsProvider
import com.github.ashutoshgngwr.noice.metrics.FirebaseCrashlyticsProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CrashlyticsProviderModule {

  @Provides
  @Singleton
  fun crashlyticsProvider(): CrashlyticsProvider {
    return FirebaseCrashlyticsProvider()
  }
}

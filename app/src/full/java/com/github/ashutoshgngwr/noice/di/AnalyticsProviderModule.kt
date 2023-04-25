package com.github.ashutoshgngwr.noice.di

import com.github.ashutoshgngwr.noice.metrics.AnalyticsProvider
import com.github.ashutoshgngwr.noice.metrics.RealAnalyticsProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsProviderModule {

  @Provides
  @Singleton
  fun analyticsProvider(): AnalyticsProvider = RealAnalyticsProvider
}

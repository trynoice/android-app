package com.github.ashutoshgngwr.noice.di

import com.github.ashutoshgngwr.noice.provider.DummyInAppBillingProvider
import com.github.ashutoshgngwr.noice.provider.InAppBillingProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object InAppBillingProviderModule {

  @Provides
  @Singleton
  fun inAppBillingProvider(): InAppBillingProvider = DummyInAppBillingProvider
}

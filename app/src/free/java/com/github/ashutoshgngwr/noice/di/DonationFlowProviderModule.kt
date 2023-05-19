package com.github.ashutoshgngwr.noice.di

import com.github.ashutoshgngwr.noice.billing.DonationFlowProvider
import com.github.ashutoshgngwr.noice.billing.OpenCollectiveDonationFlowProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DonationFlowProviderModule {

  @Provides
  @Singleton
  fun donationFlowProvider(): DonationFlowProvider {
    return OpenCollectiveDonationFlowProvider()
  }
}

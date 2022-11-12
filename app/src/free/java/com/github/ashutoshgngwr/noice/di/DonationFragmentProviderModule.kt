package com.github.ashutoshgngwr.noice.di

import com.github.ashutoshgngwr.noice.provider.DonationFragmentProvider
import com.github.ashutoshgngwr.noice.provider.OpenCollectiveDonationFragmentProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DonationFragmentProviderModule {

  @Provides
  @Singleton
  fun donationFragmentProvider(): DonationFragmentProvider = OpenCollectiveDonationFragmentProvider
}

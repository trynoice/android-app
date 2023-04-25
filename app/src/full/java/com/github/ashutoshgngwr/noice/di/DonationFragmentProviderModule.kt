package com.github.ashutoshgngwr.noice.di

import android.content.Context
import com.github.ashutoshgngwr.noice.billing.DonationFragmentProvider
import com.github.ashutoshgngwr.noice.billing.InAppDonationFragmentProvider
import com.github.ashutoshgngwr.noice.billing.OpenCollectiveDonationFragmentProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DonationFragmentProviderModule {

  @Provides
  @Singleton
  fun donationFragmentProvider(@ApplicationContext context: Context): DonationFragmentProvider {
    if (isGoogleMobileServiceAvailable(context)) {
      return InAppDonationFragmentProvider
    }

    return OpenCollectiveDonationFragmentProvider
  }
}

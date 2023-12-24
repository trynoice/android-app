package com.github.ashutoshgngwr.noice.di

import android.content.Context
import com.github.ashutoshgngwr.noice.metrics.GitHubReviewFlowProvider
import com.github.ashutoshgngwr.noice.metrics.PlayStoreReviewFlowProvider
import com.github.ashutoshgngwr.noice.metrics.ReviewFlowProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.Random
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReviewFlowProviderModule {

  @Provides
  @Singleton
  fun reviewFlowProvider(@ApplicationContext context: Context): ReviewFlowProvider {
    if (isGoogleMobileServiceAvailable(context)) {
      return PlayStoreReviewFlowProvider()
    }
    return GitHubReviewFlowProvider(Random())
  }
}

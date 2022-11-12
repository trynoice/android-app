package com.github.ashutoshgngwr.noice.di

import com.github.ashutoshgngwr.noice.provider.GitHubReviewFlowProvider
import com.github.ashutoshgngwr.noice.provider.ReviewFlowProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReviewFlowProviderModule {

  @Provides
  @Singleton
  fun reviewFlowProvider(): ReviewFlowProvider = GitHubReviewFlowProvider
}

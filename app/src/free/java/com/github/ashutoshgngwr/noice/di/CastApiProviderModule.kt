package com.github.ashutoshgngwr.noice.di

import com.github.ashutoshgngwr.noice.cast.CastApiProvider
import com.github.ashutoshgngwr.noice.cast.DummyCastApiProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CastApiProviderModule {

  @Provides
  @Singleton
  fun castApiProvider(): CastApiProvider = DummyCastApiProvider
}

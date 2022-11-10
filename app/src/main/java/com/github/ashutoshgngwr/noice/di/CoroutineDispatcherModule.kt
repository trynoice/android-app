package com.github.ashutoshgngwr.noice.di

import com.github.ashutoshgngwr.noice.AppDispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoroutineDispatcherModule {

  @Provides
  @Singleton
  fun appDispatchers(): AppDispatchers {
    return AppDispatchers(
      main = Dispatchers.Main,
      io = Dispatchers.IO,
    )
  }
}

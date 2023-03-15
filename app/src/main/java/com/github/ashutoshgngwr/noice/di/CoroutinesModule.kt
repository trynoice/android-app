package com.github.ashutoshgngwr.noice.di

import com.github.ashutoshgngwr.noice.AppDispatchers
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

  @AppCoroutineScope
  @Provides
  @Singleton
  fun appScope(): CoroutineScope {
    return MainScope()
  }

  @Provides
  @Singleton
  fun appDispatchers(): AppDispatchers {
    return AppDispatchers(
      main = Dispatchers.Main,
      io = Dispatchers.IO,
    )
  }
}

/**
 * Annotation for a application level coroutine scope.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppCoroutineScope

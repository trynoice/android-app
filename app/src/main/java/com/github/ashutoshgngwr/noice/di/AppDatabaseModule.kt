package com.github.ashutoshgngwr.noice.di

import android.content.Context
import androidx.room.Room
import com.github.ashutoshgngwr.noice.data.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppDatabaseModule {

  @Provides
  @Singleton
  fun appDatabase(@ApplicationContext context: Context): AppDatabase {
    // TODO: remove in future versions
    // clear old cache data store.
    context.deleteDatabase("app-cache.may.db")
    return Room.databaseBuilder(context, AppDatabase::class.java, "${context.packageName}.db")
      .build()
  }
}

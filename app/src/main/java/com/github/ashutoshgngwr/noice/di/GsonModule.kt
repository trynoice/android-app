package com.github.ashutoshgngwr.noice.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GsonModule {

  @Provides
  @Singleton
  fun gson(): Gson {
    return GsonBuilder().create()
  }
}

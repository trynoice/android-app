package com.github.ashutoshgngwr.noice

import android.app.Application
import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.trynoice.api.client.NoiceApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@HiltAndroidApp
class NoiceApplication : Application() {

  @Module
  @InstallIn(SingletonComponent::class)
  object GsonModule {
    @Provides
    @Singleton
    fun gson(): Gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
  }

  @Module
  @InstallIn(SingletonComponent::class)
  object ApiModule {
    @Provides
    @Singleton
    fun client(@ApplicationContext context: Context): NoiceApiClient = NoiceApiClient(context)
  }
}

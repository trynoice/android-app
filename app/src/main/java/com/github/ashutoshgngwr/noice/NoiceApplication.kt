package com.github.ashutoshgngwr.noice

import android.app.Application
import android.content.Context
import android.os.Build
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.trynoice.api.client.NoiceApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.greenrobot.eventbus.EventBus
import javax.inject.Singleton

@HiltAndroidApp
class NoiceApplication : Application() {

  @Module
  @InstallIn(SingletonComponent::class)
  object EventBusModule {
    @Provides
    @Singleton
    fun eventBus(): EventBus = EventBus.getDefault()
  }

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
    fun client(@ApplicationContext context: Context, gson: Gson): NoiceApiClient {
      return NoiceApiClient(
        context = context,
        gson = gson,
        userAgent = "${context.getString(R.string.app_name)}/${BuildConfig.VERSION_NAME} " +
          "(Android ${Build.VERSION.RELEASE}; ${Build.MANUFACTURER} ${Build.MODEL})",
      )
    }
  }
}

package com.github.ashutoshgngwr.noice.di

import android.content.Context
import android.os.Build
import com.github.ashutoshgngwr.noice.BuildConfig
import com.github.ashutoshgngwr.noice.R
import com.google.gson.Gson
import com.trynoice.api.client.NoiceApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiClientModule {

  @Provides
  @Singleton
  fun apiClient(@ApplicationContext context: Context, gson: Gson): NoiceApiClient {
    return NoiceApiClient(
      context = context,
      gson = gson,
      userAgent = "${context.getString(R.string.app_name)}/${BuildConfig.VERSION_NAME} " +
        "(Android ${Build.VERSION.RELEASE}; ${Build.MANUFACTURER} ${Build.MODEL})",
    )
  }
}

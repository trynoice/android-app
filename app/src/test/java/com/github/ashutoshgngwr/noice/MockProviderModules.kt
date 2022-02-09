package com.github.ashutoshgngwr.noice

import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.CrashlyticsProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.mockk.mockk
import javax.inject.Singleton

@Module
@TestInstallIn(
  components = [SingletonComponent::class],
  replaces = [CrashlyticsProviderModule::class]
)
object MockCrashlyticsProviderModule {
  @Provides
  @Singleton
  fun crashlyticsProvider(): CrashlyticsProvider = mockk(relaxed = true)
}

@Module
@TestInstallIn(
  components = [SingletonComponent::class],
  replaces = [AnalyticsProviderModule::class]
)
object MockAnalyticsProviderModule {
  @Provides
  @Singleton
  fun analyticsProvider(): AnalyticsProvider = mockk(relaxed = true)
}

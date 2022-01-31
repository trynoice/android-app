package com.github.ashutoshgngwr.noice

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.mockk.mockk
import org.greenrobot.eventbus.EventBus
import javax.inject.Singleton

@Module
@TestInstallIn(
  components = [SingletonComponent::class],
  replaces = [NoiceApplication.EventBusModule::class]
)
object MockEventBusModule {
  @Provides
  @Singleton
  fun eventBus(): EventBus = mockk(relaxed = true)
}

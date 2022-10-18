package com.github.ashutoshgngwr.noice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.data.models.SubscriptionPlanDto
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SubscriptionPlanDaoTest {

  private lateinit var appDb: AppDatabase
  private lateinit var subscriptionPlanDao: SubscriptionPlanDao

  @Before
  fun setUp() {
    appDb = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      AppDatabase::class.java
    ).build()

    subscriptionPlanDao = appDb.subscriptionPlans()
  }

  @After
  fun teardown() {
    appDb.close()
  }

  @Test
  fun saveAllAndList() = runTest {
    val plan1 = buildPlanDto(1, "test-1")
    val plan2 = buildPlanDto(2, "test-2")
    subscriptionPlanDao.saveAll(listOf(plan1, plan2))

    var plans = subscriptionPlanDao.list()
    assertEquals(2, plans.size)
    assertEquals(listOf(plan1, plan2), plans)

    plans = subscriptionPlanDao.list("test-1")
    assertEquals(1, plans.size)
    assertEquals(plan1, plans.firstOrNull())

    subscriptionPlanDao.saveAll(listOf(plan1, plan2.copy(provider = "test-1")))
    plans = subscriptionPlanDao.list("test-2")
    assertEquals(0, plans.size)
  }

  private fun buildPlanDto(id: Int, provider: String): SubscriptionPlanDto {
    return SubscriptionPlanDto(
      id = id,
      provider = provider,
      billingPeriodMonths = 1,
      trialPeriodDays = 15,
      priceInIndianPaise = 10000,
    )
  }
}

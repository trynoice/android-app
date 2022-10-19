package com.github.ashutoshgngwr.noice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.ashutoshgngwr.noice.data.models.SubscriptionDto
import com.github.ashutoshgngwr.noice.data.models.SubscriptionPlanDto
import com.github.ashutoshgngwr.noice.data.models.SubscriptionWithPlanDto
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.*

@RunWith(RobolectricTestRunner::class)
class SubscriptionDaoTest {

  private lateinit var appDb: AppDatabase
  private lateinit var subscriptionDao: SubscriptionDao

  @Before
  fun setUp() {
    appDb = Room.inMemoryDatabaseBuilder(
      ApplicationProvider.getApplicationContext(),
      AppDatabase::class.java
    ).build()

    subscriptionDao = appDb.subscriptions()
  }

  @After
  fun tearDown() {
    appDb.close()
  }

  @Test
  fun saveAndGet() = runTest {
    val subscription = buildSubscriptionWithPlanDto(1, 1)
    subscriptionDao.save(subscription)
    assertEquals(subscription, subscriptionDao.get(1))

    subscriptionDao.save(buildSubscriptionWithPlanDto(1, 2))
    assertNotEquals(subscription, subscriptionDao.get(1))
  }

  @Test
  fun saveAllAndGetByRenewsAfter() = runTest {
    val now = System.currentTimeMillis()
    val subscription1 = buildSubscriptionWithPlanDto(1, 1, renewsAt = now - 60000)
    val subscription2 = buildSubscriptionWithPlanDto(2, 2, renewsAt = now + 60000)
    subscriptionDao.saveAll(listOf(subscription1, subscription2))

    var actual = subscriptionDao.getByRenewsAfter(now - 120000)
    assertEquals(subscription1, actual) // should return the first renewing subscription

    actual = subscriptionDao.getByRenewsAfter(now)
    assertEquals(subscription2, actual)

    actual = subscriptionDao.getByRenewsAfter(now + 120000)
    assertNull(actual)
  }

  @Test
  fun saveAllAndListStarted() = runTest {
    val now = System.currentTimeMillis()
    val subscription1 = buildSubscriptionWithPlanDto(1, 1)
    val subscription2 = buildSubscriptionWithPlanDto(2, 2, startedAt = now)
    val subscription3 = buildSubscriptionWithPlanDto(3, 2, startedAt = now - 60000)
    subscriptionDao.saveAll(listOf(subscription1, subscription2, subscription3))

    assertEquals(listOf(subscription2, subscription3), subscriptionDao.listStarted(0, 10))
    assertEquals(listOf(subscription2), subscriptionDao.listStarted(0, 1))
    assertEquals(listOf(subscription3), subscriptionDao.listStarted(1, 10))
  }

  @Test
  fun saveAndListPlans() = runTest {
    val plan1 = buildPlanDto(1, "test-1")
    val plan2 = buildPlanDto(2, "test-2")
    subscriptionDao.savePlans(listOf(plan1, plan2))

    var plans = subscriptionDao.listPlans()
    assertEquals(2, plans.size)
    assertEquals(listOf(plan1, plan2), plans)

    plans = subscriptionDao.listPlans("test-1")
    assertEquals(1, plans.size)
    assertEquals(plan1, plans.firstOrNull())

    subscriptionDao.savePlans(listOf(plan1, plan2.copy(provider = "test-1")))
    plans = subscriptionDao.listPlans("test-2")
    assertEquals(0, plans.size)
  }

  @Test
  fun saveAllAndRemoveSubscriptions() = runTest {
    val subscription1 = buildSubscriptionWithPlanDto(1, 1)
    val subscription2 = buildSubscriptionWithPlanDto(2, 2)
    subscriptionDao.saveAll(listOf(subscription1, subscription2))

    subscriptionDao.removeAll()
    assertNull(subscriptionDao.get(1))
    assertNull(subscriptionDao.get(2))
  }

  private fun buildSubscriptionWithPlanDto(
    id: Long,
    planId: Int,
    startedAt: Long? = null,
    renewsAt: Long? = null,
  ): SubscriptionWithPlanDto {
    return SubscriptionWithPlanDto(
      subscription = SubscriptionDto(
        id = id,
        planId = planId,
        isActive = false,
        isPaymentPending = false,
        isAutoRenewing = false,
        startedAt = startedAt?.let { Date(it) },
        renewsAt = renewsAt?.let { Date(it) }
      ),
      plan = buildPlanDto(planId, "test")
    )
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

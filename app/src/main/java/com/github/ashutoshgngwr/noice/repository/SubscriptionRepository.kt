package com.github.ashutoshgngwr.noice.repository

import android.app.Activity
import android.util.Log
import com.github.ashutoshgngwr.noice.provider.SubscriptionProvider
import com.github.ashutoshgngwr.noice.repository.errors.AlreadySubscribedError
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.SubscriptionNotFoundError
import com.trynoice.api.client.models.Subscription
import com.trynoice.api.client.models.SubscriptionPlan
import io.github.ashutoshgngwr.may.May
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements a data access layer for fetching and manipulating subscription related data.
 */
@Singleton
class SubscriptionRepository @Inject constructor(
  private val subscriptionProvider: SubscriptionProvider,
  private val cacheStore: May,
) {

  /**
   * Returns a [Flow] that emits a list of available subscription plans as a [Resource].
   *
   * On failures, the flow emits [Resource.Failure] with:
   * - [NetworkError] on network errors.
   * - [HttpException] on api errors.
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun getPlans(): Flow<Resource<List<SubscriptionPlan>>> = fetchNetworkBoundResource(
    loadFromCache = { cacheStore.getAs(PLANS_CACHE_KEY) },
    loadFromNetwork = { subscriptionProvider.getPlans() },
    cacheNetworkResult = { plans -> cacheStore.put(PLANS_CACHE_KEY, plans) },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "getPlans:", e)
      when (e) {
        is IOException -> NetworkError
        else -> e
      }
    },
  )

  /**
   * Returns a [Flow] that emits the current state of billing flow launch operation as a [Resource].
   *
   * On failures, the flow emits [Resource.Failure] with:
   *  - [AlreadySubscribedError] when the current user already owns an active subscription.
   *  - [NetworkError] on network errors.
   *  - [HttpException] on api errors.
   *  - [com.github.ashutoshgngwr.noice.provider.InAppBillingProviderException] on in-app billing
   *    errors when using Google Play implementation of the [SubscriptionProvider].
   *
   *  @see fetchNetworkBoundResource
   *  @see Resource
   */
  fun launchBillingFlow(
    activity: Activity,
    plan: SubscriptionPlan,
  ): Flow<Resource<Unit>> = fetchNetworkBoundResource(
    loadFromNetwork = { subscriptionProvider.launchBillingFlow(activity, plan) },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "launchSubscriptionFlow:", e)
      when {
        e is HttpException && e.code() == 409 -> AlreadySubscribedError
        e is IOException -> NetworkError
        else -> e
      }
    },
  )

  /**
   * Returns a [Flow] that emits the requested subscription as a [Resource].
   *
   * On failures, the flow emits [Resource.Failure] with:
   * - [SubscriptionNotFoundError] when the subscription with requested id doesn't exist.
   * - [NetworkError] on network errors.
   * - [HttpException] on api errors.
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun get(subscriptionId: Long): Flow<Resource<Subscription>> = fetchNetworkBoundResource(
    loadFromCache = { cacheStore.getAs("${SUBSCRIPTION_KEY_PREFIX}/${subscriptionId}") },
    loadFromNetwork = { subscriptionProvider.getSubscription(subscriptionId) },
    cacheNetworkResult = { s -> cacheStore.put("${SUBSCRIPTION_KEY_PREFIX}/${subscriptionId}", s) },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "get:", e)
      when {
        e is HttpException && e.code() == 404 -> SubscriptionNotFoundError
        e is IOException -> NetworkError
        else -> e
      }
    },
  )

  /**
   * Returns a [Flow] that emits the requested subscription page as a [Resource].
   *
   * On failures, the flow emits [Resource.Failure] with:
   * - [SubscriptionNotFoundError] when page with given index doesn't exist.
   * - [NetworkError] on network errors.
   * - [HttpException] on api errors.
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun list(page: Int = 0): Flow<Resource<List<Subscription>>> = fetchNetworkBoundResource(
    loadFromCache = { cacheStore.getAs("${SUBSCRIPTION_PAGE_PREFIX}/$page") },
    loadFromNetwork = { subscriptionProvider.listSubscription(false, page) },
    cacheNetworkResult = { cacheStore.put("${SUBSCRIPTION_PAGE_PREFIX}/$page", it) },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "list:", e)
      when {
        e is HttpException && e.code() == 404 -> SubscriptionNotFoundError
        e is IOException -> NetworkError
        else -> e
      }
    },
  )

  /**
   * Returns a [Flow] that emits the current state of subscription cancel operation as a [Resource].
   *
   * On failures, the flow emits [Resource.Failure] with:
   * - [SubscriptionNotFoundError] when the subscription isn't owned by the auth user or is inactive.
   * - [NetworkError] on network errors.
   * - [HttpException] on api errors.
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun cancel(subscription: Subscription): Flow<Resource<Unit>> = fetchNetworkBoundResource(
    loadFromNetwork = { subscriptionProvider.cancelSubscription(subscription.id) },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "cancel:", e)
      when {
        e is HttpException && e.code() == 404 -> SubscriptionNotFoundError
        e is IOException -> NetworkError
        else -> e
      }
    },
  )

  /**
   * Returns whether the given [subscription] is manageable through an external portal, e.g. Stripe
   * customer portal.
   *
   * @see launchManagementFlow
   */
  fun canLaunchManagementFlow(subscription: Subscription): Boolean {
    return subscriptionProvider.canLaunchManagementFlow(subscription)
  }

  /**
   * Launches an intent to open the external portal for managing the subscription.
   */
  fun launchManagementFlow(activity: Activity, subscription: Subscription) {
    subscriptionProvider.launchManagementFlow(activity, subscription)
  }

  /**
   * Returns whether the given [subscription] can be upgraded via an internal flow, e.g. Google Play
   * In-App billing flow.
   */
  fun canLaunchUpgradeFlow(subscription: Subscription): Boolean {
    return subscriptionProvider.canLaunchUpgradeFlow(subscription)
  }

  companion object {
    private const val LOG_TAG = "SubscriptionRepository"
    private const val SUBSCRIPTION_KEY_PREFIX = "subscription"
    private const val PLANS_CACHE_KEY = "${SUBSCRIPTION_KEY_PREFIX}/plans"
    private const val SUBSCRIPTION_PAGE_PREFIX = "${SUBSCRIPTION_KEY_PREFIX}/page"
  }
}

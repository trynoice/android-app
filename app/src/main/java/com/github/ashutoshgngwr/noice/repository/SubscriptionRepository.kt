package com.github.ashutoshgngwr.noice.repository

import android.app.Activity
import android.net.Uri
import android.util.Log
import com.github.ashutoshgngwr.noice.fragment.SubscriptionPurchaseListFragment
import com.github.ashutoshgngwr.noice.provider.SubscriptionBillingProvider
import com.github.ashutoshgngwr.noice.repository.errors.AlreadySubscribedError
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.SubscriptionNotFoundError
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.Subscription
import com.trynoice.api.client.models.SubscriptionPlan
import io.github.ashutoshgngwr.may.May
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.onEach
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Implements a data access layer for fetching and manipulating subscription related data.
 */
@Singleton
class SubscriptionRepository @Inject constructor(
  private val subscriptionBillingProvider: SubscriptionBillingProvider,
  private val apiClient: NoiceApiClient,
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
    loadFromNetwork = { subscriptionBillingProvider.getPlans() },
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
    activeSubscription: Subscription?,
  ): Flow<Resource<Unit>> = fetchNetworkBoundResource(
    loadFromNetwork = {
      subscriptionBillingProvider.launchBillingFlow(activity, plan, activeSubscription)
    },
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
    loadFromNetwork = {
      apiClient.subscriptions().get(subscriptionId, stripeReturnUrl = STRIPE_RETURN_URL)
    },
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
   * Returns a [Flow] that emits the currently active subscription as a [Resource].
   *
   * On failures, the flow emits [Resource.Failure] with:
   * - [SubscriptionNotFoundError] when the user doesn't own an active subscription.
   * - [NetworkError] on network errors.
   * - [HttpException] on api errors.
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun getActive(): Flow<Resource<Subscription>> = fetchNetworkBoundResource(
    loadFromNetwork = {
      apiClient.subscriptions()
        .list(onlyActive = true, stripeReturnUrl = STRIPE_RETURN_URL)
        .firstOrNull()
        ?: throw SubscriptionNotFoundError
    },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "getActive:", e)
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
   * - [NetworkError] on network errors.
   * - [HttpException] on api errors.
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun list(page: Int = 0): Flow<Resource<List<Subscription>>> = fetchNetworkBoundResource(
    loadFromNetwork = {
      apiClient.subscriptions().list(false, page, stripeReturnUrl = STRIPE_RETURN_URL)
    },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "list:", e)
      when (e) {
        is IOException -> NetworkError
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
    loadFromNetwork = { apiClient.subscriptions().cancel(subscription.id) },
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
   * Returns a flow that actively polls the API server and emits whether the authenticated user owns
   * an active subscription as a [Resource].
   *
   * On failures, the flow emits [Resource.Failure] with:
   * - [NetworkError] on network errors.
   * - [HttpException] on api errors.
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun isSubscribed(): Flow<Resource<Boolean>> = flow {
    while (true) {
      val r = getActive()
        .onEach { r ->
          emit(
            when {
              r is Resource.Loading -> Resource.Loading(r.data != null)
              r is Resource.Success -> Resource.Success(r.data != null)
              r.error is SubscriptionNotFoundError -> Resource.Success(false)
              r.error is HttpException && r.error.code() == 401 -> Resource.Success(false) // unauthenticated.
              else -> Resource.Failure(requireNotNull(r.error), r.data != null)
            }
          )
        }
        .lastOrNull()

      val expiresAt = r?.data?.renewsAt?.time ?: Long.MAX_VALUE
      delay(min(60_000L, expiresAt - System.currentTimeMillis()))
    }
  }

  companion object {
    private const val LOG_TAG = "SubscriptionRepository"
    private const val SUBSCRIPTION_KEY_PREFIX = "subscription"
    private const val PLANS_CACHE_KEY = "${SUBSCRIPTION_KEY_PREFIX}/plans"

    private val STRIPE_RETURN_URL = Uri.parse("https://trynoice.com/redirect")
      .buildUpon()
      .appendQueryParameter("uri", SubscriptionPurchaseListFragment.URI)
      .toString()
  }
}

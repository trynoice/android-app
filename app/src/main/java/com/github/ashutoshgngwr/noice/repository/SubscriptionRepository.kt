package com.github.ashutoshgngwr.noice.repository

import android.app.Activity
import android.net.Uri
import android.util.Log
import com.github.ashutoshgngwr.noice.fragment.SubscriptionPurchaseListFragment
import com.github.ashutoshgngwr.noice.provider.SubscriptionBillingProvider
import com.github.ashutoshgngwr.noice.repository.errors.AlreadySubscribedError
import com.github.ashutoshgngwr.noice.repository.errors.GiftCardExpiredError
import com.github.ashutoshgngwr.noice.repository.errors.GiftCardNotFoundError
import com.github.ashutoshgngwr.noice.repository.errors.GiftCardRedeemedError
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.SubscriptionNotFoundError
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.GiftCard
import com.trynoice.api.client.models.Subscription
import com.trynoice.api.client.models.SubscriptionPlan
import io.github.ashutoshgngwr.may.May
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.onEach
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.time.DurationUnit
import kotlin.time.toDuration

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
  fun listPlans(
    currencyCode: String? = null,
  ): Flow<Resource<List<SubscriptionPlan>>> = fetchNetworkBoundResource(
    loadFromCache = { cacheStore.getAs(PLANS_CACHE_KEY) },
    loadFromNetwork = { subscriptionBillingProvider.listPlans(currencyCode) },
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
  fun get(
    subscriptionId: Long,
    currencyCode: String? = null,
  ): Flow<Resource<Subscription>> = fetchNetworkBoundResource(
    loadFromCache = { cacheStore.getAs("${SUBSCRIPTION_KEY_PREFIX}/${subscriptionId}") },
    loadFromNetwork = { apiClient.subscriptions().get(subscriptionId, currency = currencyCode) },
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
    loadFromCache = { cacheStore.getAs("${SUBSCRIPTION_KEY_PREFIX}/active") },
    loadFromNetwork = {
      apiClient.subscriptions()
        .list(onlyActive = true)
        .firstOrNull()
        ?: throw SubscriptionNotFoundError
    },
    cacheNetworkResult = { cacheStore.put("${SUBSCRIPTION_KEY_PREFIX}/active", it) },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "getActive:", e)
      when (e) {
        is IOException -> NetworkError
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
  fun list(
    page: Int = 0,
    currencyCode: String? = null,
  ): Flow<Resource<List<Subscription>>> = fetchNetworkBoundResource(
    loadFromCache = { cacheStore.getAs("${SUBSCRIPTION_KEY_PREFIX}/page/$page") },
    loadFromNetwork = {
      apiClient.subscriptions()
        .list(false, page = page, currency = currencyCode)
    },
    cacheNetworkResult = { cacheStore.put("${SUBSCRIPTION_KEY_PREFIX}/page/$page", it) },
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
  fun pollSubscriptionStatus(): Flow<Resource<Boolean>> = flow {
    var first = true
    while (true) {
      val r = getActive()
        // consider only the first loading event and ignore latter loading events.
        .filterNot { !first && it is Resource.Loading }
        .onEach { r ->
          emit(
            when {
              r is Resource.Loading -> Resource.Loading(r.data != null)
              r is Resource.Success -> Resource.Success(r.data != null)
              r.error is SubscriptionNotFoundError -> Resource.Success(false)
              r.error is HttpException && r.error.code() == 401 -> Resource.Success(false) // unauthenticated.
              else -> Resource.Failure(r.error ?: IllegalStateException(), r.data != null)
            }
          )
        }
        .lastOrNull()

      first = false
      val expiresAt = r?.data?.renewsAt?.time
      val delay = if (expiresAt == null || expiresAt < System.currentTimeMillis()) {
        60_000L
      } else {
        min(60_000L, expiresAt - System.currentTimeMillis())
      }.toDuration(DurationUnit.MILLISECONDS)

      Log.d(LOG_TAG, "pollSubscriptionStatus: scheduling poll after $delay")
      delay(delay)
    }
  }

  /**
   * Returns a [Flow] that emits the requested gift card as a [Resource].
   *
   * On failures, the flow emits [Resource.Failure] with:
   * - [GiftCardNotFoundError] when the gift card with the requested code doesn't exist.
   * - [NetworkError] on network errors.
   * - [HttpException] on api errors.
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun getGiftCard(code: String): Flow<Resource<GiftCard>> = fetchNetworkBoundResource(
    loadFromNetwork = { apiClient.subscriptions().getGiftCard(code) },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "getGiftCard:", e)
      when {
        e is HttpException && e.code() == 404 -> GiftCardNotFoundError
        e is IOException -> NetworkError
        else -> e
      }
    },
  )

  /**
   * Returns a [Flow] that emits the current state of gift card redeem operation as a [Resource].
   *
   * On failures, the flow emits [Resource.Failure] with:
   * - [GiftCardNotFoundError] when the gift card with the requested code doesn't exist.
   * - [AlreadySubscribedError] when the user already owns another active subscription.
   * - [GiftCardExpiredError]  when the gift card with the requested code has expired.
   * - [GiftCardRedeemedError] when the gift card with the requested code has already been redeemed.
   * - [NetworkError] on network errors.
   * - [HttpException] on api errors.
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun redeemGiftCard(card: GiftCard): Flow<Resource<Unit>> = fetchNetworkBoundResource(
    loadFromNetwork = { apiClient.subscriptions().redeemGiftCard(card.code) },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "redeemGiftCard:", e)
      when {
        e is HttpException && e.code() == 404 -> GiftCardNotFoundError
        e is HttpException && e.code() == 409 -> AlreadySubscribedError
        e is HttpException && e.code() == 410 -> GiftCardExpiredError
        e is HttpException && e.code() == 422 -> GiftCardRedeemedError // gift card has already been redeemed.
        e is IOException -> NetworkError
        else -> e
      }
    },
  )

  /**
   * Returns a [Flow] that emits the current state of stripe customer portal url operation as a
   * [Resource].
   *
   * On failures, the flow emits [Resource.Failure] with:
   * - [NetworkError] on network errors.
   * - [HttpException] on api errors.
   *
   * @see fetchNetworkBoundResource
   * @see Resource
   */
  fun stripeCustomerPortalUrl(): Flow<Resource<String>> = fetchNetworkBoundResource(
    loadFromNetwork = {
      apiClient.subscriptions()
        .stripeCustomerPortalUrl(STRIPE_RETURN_URL)
        .url
    },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "stripeCustomerPortalUrl:", e)
      when (e) {
        is IOException -> NetworkError
        else -> e
      }
    },
  )

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

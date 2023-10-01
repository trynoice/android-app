package com.github.ashutoshgngwr.noice.repository

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.map
import androidx.room.withTransaction
import com.github.ashutoshgngwr.noice.billing.SubscriptionBillingProvider
import com.github.ashutoshgngwr.noice.data.AppDatabase
import com.github.ashutoshgngwr.noice.data.models.SubscriptionWithPlanDto
import com.github.ashutoshgngwr.noice.ext.bindServiceCallbackFlow
import com.github.ashutoshgngwr.noice.fragment.SubscriptionPurchasesFragment
import com.github.ashutoshgngwr.noice.models.GiftCard
import com.github.ashutoshgngwr.noice.models.Subscription
import com.github.ashutoshgngwr.noice.models.SubscriptionPlan
import com.github.ashutoshgngwr.noice.models.toDomainEntity
import com.github.ashutoshgngwr.noice.models.toRoomDto
import com.github.ashutoshgngwr.noice.repository.errors.AlreadySubscribedError
import com.github.ashutoshgngwr.noice.repository.errors.GiftCardExpiredError
import com.github.ashutoshgngwr.noice.repository.errors.GiftCardNotFoundError
import com.github.ashutoshgngwr.noice.repository.errors.GiftCardRedeemedError
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.SubscriptionNotFoundError
import com.github.ashutoshgngwr.noice.service.SubscriptionStatusPollService
import com.github.ashutoshgngwr.noice.service.SubscriptionStatusPollServiceBinder
import com.trynoice.api.client.NoiceApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements a data access layer for fetching and manipulating subscription related data.
 */
@Singleton
class SubscriptionRepository @Inject constructor(
  @ApplicationContext private val context: Context,
  private val billingProvider: SubscriptionBillingProvider,
  private val apiClient: NoiceApiClient,
  private val appDb: AppDatabase,
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
    loadFromCache = {
      appDb.subscriptions()
        .listPlans(billingProvider.getId())
        .toDomainEntity()
    },
    loadFromNetwork = {
      apiClient.subscriptions()
        .listPlans(provider = billingProvider.getId(), currency = currencyCode)
        .toDomainEntity()
    },
    cacheNetworkResult = { appDb.subscriptions().savePlans(it.toRoomDto()) },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "listPlans:", e)
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
    loadFromNetwork = { billingProvider.launchBillingFlow(activity, plan, activeSubscription) },
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
    loadFromCache = { appDb.subscriptions().get(subscriptionId)?.toDomainEntity() },
    loadFromNetwork = {
      apiClient.subscriptions()
        .get(subscriptionId, currency = currencyCode)
        .toDomainEntity()
    },
    cacheNetworkResult = { appDb.subscriptions().save(it.toRoomDto()) },
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
    loadFromCache = {
      appDb.subscriptions()
        .getByRenewsAfter(System.currentTimeMillis())
        ?.toDomainEntity()
    },
    loadFromNetwork = {
      apiClient.subscriptions()
        .list(onlyActive = true)
        .firstOrNull()
        ?.toDomainEntity()
        ?: throw SubscriptionNotFoundError
    },
    cacheNetworkResult = { appDb.subscriptions().save(it.toRoomDto()) },
    loadFromNetworkErrorTransform = { e ->
      Log.i(LOG_TAG, "getActive:", e)
      when (e) {
        is IOException -> NetworkError
        else -> e
      }
    },
  )

  /**
   * @return a [Flow] that emits PagingData for subscription purchase history of the current user.
   */
  fun pagingDataFlow(currencyCode: String? = null): Flow<PagingData<Subscription>> = Pager(
    config = PagingConfig(pageSize = 20),
    remoteMediator = object : RemoteMediator<Int, SubscriptionWithPlanDto>() {
      private var currentPage = -1

      override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, SubscriptionWithPlanDto>,
      ): MediatorResult {
        if (loadType == LoadType.PREPEND) {
          return MediatorResult.Success(endOfPaginationReached = true)
        }

        currentPage = if (loadType == LoadType.REFRESH) 0 else currentPage + 1
        return try {
          val subscriptions = apiClient.subscriptions()
            .list(page = currentPage, currency = currencyCode)
            .toRoomDto()

          appDb.withTransaction {
            if (loadType == LoadType.REFRESH) appDb.subscriptions().removeAll()
            appDb.subscriptions().saveAll(subscriptions)
          }

          MediatorResult.Success(endOfPaginationReached = subscriptions.isEmpty())
        } catch (e: Throwable) {
          Log.i(LOG_TAG, "pagingDataFlow:", e)
          MediatorResult.Error(e)
        }
      }
    }
  ) { appDb.subscriptions().pagingSource() }
    .flow
    .map { pagingData -> pagingData.map { it.toDomainEntity() } }

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
   * Returns a flow that actively polls the API client for user's current subscription status and
   * emits it.
   */
  fun isSubscribed(): Flow<Boolean> =
    context.bindServiceCallbackFlow<SubscriptionStatusPollService, SubscriptionStatusPollServiceBinder, Boolean> { binder ->
      binder.isSubscribed
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
    loadFromNetwork = { apiClient.subscriptions().getGiftCard(code).toDomainEntity() },
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

    private val STRIPE_RETURN_URL = Uri.parse("https://trynoice.com/redirect")
      .buildUpon()
      .appendQueryParameter("uri", SubscriptionPurchasesFragment.URI)
      .toString()
  }
}

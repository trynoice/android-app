package com.github.ashutoshgngwr.noice.billing

import android.app.Activity
import androidx.room.withTransaction
import com.github.ashutoshgngwr.noice.AppDispatchers
import com.github.ashutoshgngwr.noice.data.AppDatabase
import com.github.ashutoshgngwr.noice.data.models.SubscriptionPurchaseNotificationDto
import com.github.ashutoshgngwr.noice.models.Subscription
import com.github.ashutoshgngwr.noice.models.SubscriptionPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * An abstraction layer to allow application to select from Stripe and Google Play subscription
 * providers based on the build variant and Google Play billing service availability.
 */
abstract class SubscriptionBillingProvider(
  private val appDb: AppDatabase,
  private val defaultScope: CoroutineScope,
  private val appDispatchers: AppDispatchers,
) {

  private var listener: Listener? = null

  /**
   * @return the identifier used in [SubscriptionPlan.provider].
   */
  abstract fun getId(): String

  /**
   * Initiates the subscription billing flow by requesting the API to create a new subscription
   * entity. It then executes provider specific code to launch provider's billing flow. It returns
   * once the billing flow is successfully launched.
   *
   * If [activeSubscription] is not `null`, it initiates a subscription upgrade flow to change the
   * billing plan for the [activeSubscription].
   *
   * @throws retrofit2.HttpException on API error. For details about HTTP error codes, refer
   * [create-subscription operation][com.trynoice.api.client.apis.SubscriptionApi.create].
   * @throws java.io.IOException on network error.
   * @throws SubscriptionBillingProviderException on failure to launch billing flow.
   *
   * @see com.trynoice.api.client.apis.SubscriptionApi.create
   */
  abstract suspend fun launchBillingFlow(
    activity: Activity,
    plan: SubscriptionPlan,
    activeSubscription: Subscription?,
  )

  /**
   * Returns whether [s] can be upgraded using in-app flows offered by the current billing provider
   * implementation.
   */
  abstract fun isUpgradeable(s: Subscription): Boolean

  /**
   * Registers a subscription purchase listener.
   */
  fun setListener(listener: Listener) {
    this.listener = listener
    notifyListener()
  }

  /**
   * Removes any registered subscription purchase listener.
   */
  fun removeListener() {
    this.listener = null
  }

  protected fun notifyPurchase(subscriptionId: Long, isPending: Boolean) {
    defaultScope.launch(appDispatchers.io) {
      appDb.subscriptionPurchaseNotifications()
        .save(SubscriptionPurchaseNotificationDto(subscriptionId, isPending, false))

      notifyListener()
    }
  }

  private fun notifyListener() {
    if (listener == null) {
      return
    }

    defaultScope.launch(appDispatchers.io) {
      appDb.withTransaction {
        appDb.subscriptionPurchaseNotifications()
          .listUnconsumed()
          .forEach { n ->
            withContext(appDispatchers.main) {
              if (n.isPurchasePending) {
                listener?.onSubscriptionPurchasePending(n.subscriptionId)
              } else {
                listener?.onSubscriptionPurchaseComplete(n.subscriptionId)
              }
            }

            appDb.subscriptionPurchaseNotifications()
              .save(n.copy(isConsumed = listener != null))
          }

        appDb.subscriptionPurchaseNotifications()
          .removeConsumed()
      }
    }
  }

  interface Listener {

    /**
     * Invoked when a new subscription purchase is made but its payment is delayed.
     *
     * @param subscriptionId id of the subscription as identified by the API Server.
     */
    fun onSubscriptionPurchasePending(subscriptionId: Long)

    /**
     * Invoked when a (old or new) subscription purchase's payment is processed.
     *
     * @param subscriptionId id of the subscription as identified by the API Server.
     */
    fun onSubscriptionPurchaseComplete(subscriptionId: Long)
  }
}

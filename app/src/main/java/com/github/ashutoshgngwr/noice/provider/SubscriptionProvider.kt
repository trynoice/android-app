package com.github.ashutoshgngwr.noice.provider

import android.app.Activity
import android.content.Intent
import androidx.core.net.toUri
import com.github.ashutoshgngwr.noice.fragment.SubscriptionBillingCallbackFragment
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.Subscription
import com.trynoice.api.client.models.SubscriptionFlowParams
import com.trynoice.api.client.models.SubscriptionPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An abstraction layer to allow application to select from Stripe and Google Play subscription
 * providers based on the build variant and Google Play billing service availability.
 */
abstract class SubscriptionProvider(protected val apiClient: NoiceApiClient) {

  /**
   * @return a list of subscription plans offered by the given [SubscriptionProvider].
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  abstract suspend fun getPlans(): List<SubscriptionPlan>

  /**
   * Initiates the subscription billing flow by requesting the API to create a new subscription
   * entity. It then executes provider specific code to launch provider's billing flow. It returns
   * once the billing flow is successfully launched.
   *
   * @throws retrofit2.HttpException on API error. For details about HTTP error codes, refer
   * [create-subscription operation][com.trynoice.api.client.apis.SubscriptionApi.create].
   * @throws java.io.IOException on network error.
   * @throws InAppBillingProviderException on in-app billing errors when using google play
   * subscription provider implementation.
   */
  abstract suspend fun launchBillingFlow(activity: Activity, plan: SubscriptionPlan)

  /**
   * Retrieves a subscription with the given [subscriptionId].
   *
   * @param subscriptionId id of the subscription entity.
   * @return the request [Subscription] entity.
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  suspend fun getSubscription(subscriptionId: Long): Subscription {
    return apiClient.subscriptions().get(subscriptionId, STRIPE_RETURN_URL)
  }

  /**
   * Retrieves a page of subscription purchase.
   *
   * @param onlyActive return only the active subscription (single instance).
   * @param page 0-indexed page number.
   * @return a page of [Subscription] entities.
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  suspend fun listSubscription(onlyActive: Boolean, page: Int = 0): List<Subscription> {
    return apiClient.subscriptions().list(onlyActive, page, STRIPE_RETURN_URL)
  }

  /**
   * Returns whether a given [subscription] is manageable through an external portal, e.g. Stripe
   * customer portal.
   *
   * @see launchManagementFlow
   */
  fun canLaunchManagementFlow(subscription: Subscription): Boolean {
    return subscription.isActive && subscription.plan.provider == SubscriptionPlan.PROVIDER_STRIPE
  }

  /**
   * Returns whether a given [subscription] can be upgraded via an internal flow, e.g. Google Play
   * In-App billing flow.
   */
  fun canLaunchUpgradeFlow(subscription: Subscription): Boolean {
    return subscription.isActive && subscription.plan.provider == SubscriptionPlan.PROVIDER_GOOGLE_PLAY
  }

  /**
   * Launches an intent to open the Stripe's customer portal for managing Stripe subscriptions.
   */
  fun launchManagementFlow(activity: Activity, subscription: Subscription) {
    val stripeCustomerPortalUrl = requireNotNull(subscription.stripeCustomerPortalUrl) {
      "stripe customer portal url is null for the given subscription"
    }

    activity.startActivity(
      Intent(Intent.ACTION_VIEW)
        .setData(stripeCustomerPortalUrl.toUri())
    )
  }

  companion object {
    private const val STRIPE_RETURN_URL = "https://trynoice.com/subscriptions"
  }
}

/**
 * [SubscriptionProvider] implementation that provides subscriptions using Stripe as the billing
 * provider.
 */
class StripeSubscriptionProvider(apiClient: NoiceApiClient) : SubscriptionProvider(apiClient) {

  override suspend fun getPlans(): List<SubscriptionPlan> {
    return apiClient.subscriptions().getPlans(SubscriptionPlan.PROVIDER_STRIPE)
  }

  override suspend fun launchBillingFlow(activity: Activity, plan: SubscriptionPlan) {
    require(plan.provider == SubscriptionPlan.PROVIDER_STRIPE) {
      "stripe provider launched subscription flow for non-stripe plan"
    }

    val result = apiClient.subscriptions().create(
      SubscriptionFlowParams(
        planId = plan.id,
        successUrl = SubscriptionBillingCallbackFragment.STRIPE_SUCCESS_CALLBACK_URL,
        cancelUrl = SubscriptionBillingCallbackFragment.STRIPE_CANCEL_CALLBACK_URL,
      )
    )

    val checkoutSessionUrl = requireNotNull(result.stripeCheckoutSessionUrl) {
      "stripeCheckoutSessionUrl must not be null for stripe subscription flow result."
    }

    withContext(Dispatchers.Main) {
      activity.startActivity(
        Intent(Intent.ACTION_VIEW)
          .setData(checkoutSessionUrl.toUri())
      )
    }
  }
}

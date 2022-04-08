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
interface SubscriptionBillingProvider {

  /**
   * @return a list of subscription plans offered by a given provider.
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   *
   * @see com.trynoice.api.client.apis.SubscriptionApi.getPlans
   */
  suspend fun getPlans(): List<SubscriptionPlan>

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
   *
   * @see com.trynoice.api.client.apis.SubscriptionApi.create
   */
  suspend fun launchBillingFlow(
    activity: Activity,
    plan: SubscriptionPlan,
    activeSubscription: Subscription?,
  )
}

/**
 * [SubscriptionBillingProvider] implementation that provides subscriptions using Stripe as the
 * billing provider.
 */
class StripeSubscriptionBillingProvider(
  private val apiClient: NoiceApiClient
) : SubscriptionBillingProvider {

  override suspend fun getPlans(): List<SubscriptionPlan> {
    return apiClient.subscriptions().getPlans(SubscriptionPlan.PROVIDER_STRIPE)
  }

  override suspend fun launchBillingFlow(
    activity: Activity,
    plan: SubscriptionPlan,
    activeSubscription: Subscription?,
  ) {
    require(plan.provider == SubscriptionPlan.PROVIDER_STRIPE) {
      "stripe provider launched subscription flow for non-stripe plan"
    }

    require(activeSubscription == null) {
      "stripe provider doesn't support upgrading subscription plans"
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

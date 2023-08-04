package com.github.ashutoshgngwr.noice.billing

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.github.ashutoshgngwr.noice.AppDispatchers
import com.github.ashutoshgngwr.noice.data.AppDatabase
import com.github.ashutoshgngwr.noice.di.AppCoroutineScope
import com.github.ashutoshgngwr.noice.ext.startCustomTab
import com.github.ashutoshgngwr.noice.models.Subscription
import com.github.ashutoshgngwr.noice.models.SubscriptionPlan
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.SubscriptionFlowParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SubscriptionBillingProvider] implementation that provides subscriptions using Stripe as the
 * billing provider.
 */
@Singleton
class StripeSubscriptionBillingProvider @Inject constructor(
  private val apiClient: NoiceApiClient,
  appDb: AppDatabase,
  @AppCoroutineScope appScope: CoroutineScope,
  private val appDispatchers: AppDispatchers,
) : SubscriptionBillingProvider(appDb, appScope, appDispatchers) {

  private var billingFlowStarterComponent: ComponentName? = null

  override fun getId(): String = SubscriptionPlan.PROVIDER_STRIPE

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
        successUrl = SUCCESS_REDIRECT_URL,
        cancelUrl = CANCEL_REDIRECT_URL,
      )
    )

    val checkoutSessionUrl = requireNotNull(result.stripeCheckoutSessionUrl) {
      "stripeCheckoutSessionUrl must not be null for stripe subscription flow result."
    }

    billingFlowStarterComponent = activity.componentName
    withContext(appDispatchers.main) {
      activity.startCustomTab(checkoutSessionUrl)
    }
  }

  override fun isUpgradeable(s: Subscription): Boolean {
    return false
  }

  fun handleStripeCheckoutSessionCallbackUri(context: Context, uri: Uri) {
    val subscriptionId = uri.getQueryParameter(SUBSCRIPTION_ID_PARAM)?.toLongOrNull()
    if (!uri.toString().startsWith(BASE_URI)) {
      return
    }

    // dismiss custom tab by bringing caller activity to the top of the stack.
    billingFlowStarterComponent
      ?.let {
        Intent()
          .setComponent(it)
          .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
      }
      ?.also { context.startActivity(it) }

    if (uri.getQueryParameter(ACTION_PARAM) == ACTION_SUCCESS && subscriptionId != null) {
      notifyPurchase(subscriptionId, false)
    }
  }

  companion object {
    private const val SUBSCRIPTION_ID_PARAM = "subscriptionId"
    private const val ACTION_PARAM = "action"
    private const val ACTION_SUCCESS = "success"
    private const val ACTION_CANCEL = "cancel"
    private const val BASE_URI = "noice://subscriptions/stripe/callback"

    private const val CANCEL_URI = "${BASE_URI}?${ACTION_PARAM}=${ACTION_CANCEL}"
    private const val SUCCESS_URI = "${BASE_URI}?${ACTION_PARAM}=${ACTION_SUCCESS}" +
      "&${SUBSCRIPTION_ID_PARAM}={subscriptionId}"

    private val CANCEL_REDIRECT_URL = "https://trynoice.com/redirect?uri=${Uri.encode(CANCEL_URI)}"
    private val SUCCESS_REDIRECT_URL = "https://trynoice.com/redirect?" +
      "uri=${Uri.encode(SUCCESS_URI)}"
  }
}

package com.github.ashutoshgngwr.noice.billing

/**
 * Thrown by [SubscriptionBillingProvider] operations.
 */
class SubscriptionBillingProviderException(
  msg: String,
  cause: Throwable? = null
) : Exception(msg, cause)

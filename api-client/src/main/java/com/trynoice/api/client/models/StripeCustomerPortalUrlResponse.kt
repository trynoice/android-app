package com.trynoice.api.client.models

/**
 * A JSON object containing the URL to access the customer portal.
 *
 * @property url short-lived URL of the session that provides access to the customer portal.
 */
data class StripeCustomerPortalUrlResponse(val url: String)

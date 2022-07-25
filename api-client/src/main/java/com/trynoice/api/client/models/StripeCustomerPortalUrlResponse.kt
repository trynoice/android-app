package com.trynoice.api.client.models

import com.google.gson.annotations.Expose
import java.io.Serializable

/**
 * A JSON object containing the URL to access the customer portal.
 *
 * @param url short-lived URL of the session that provides access to the customer portal.
 */
data class StripeCustomerPortalUrlResponse(@Expose val url: String) : Serializable

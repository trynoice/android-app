package com.trynoice.api.client.apis

import com.trynoice.api.client.auth.annotations.NeedsAccessToken
import com.trynoice.api.client.models.SubscriptionFlowParams
import com.trynoice.api.client.models.SubscriptionFlowResult
import com.trynoice.api.client.models.SubscriptionPlan
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * APIs related to subscription management.
 */
interface SubscriptionApi {

  /**
   * Lists all available subscription plans that an auth user can subscribe.
   *
   * The `provider` parameter (case-insensitive) can have the following values.
   *  - empty or `null`: the response contains all available plans.
   *  - `google_play`: the response contains all available plans supported by Google Play.
   *  - `stripe`: the response contains all available plans supported by Stripe.
   *
   * Responses:
   *  - 200: a list of available subscription plans.
   *  - 400: request is not valid.
   *  - 422: the requested provider is not supported.
   *  - 500: internal server error.
   *
   * @param provider filter listed plans by the given provider. (optional)
   * @return [List]<[SubscriptionPlan]>
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  @GET("/v1/subscriptions/plans")
  suspend fun getPlans(@Query("provider") provider: String? = null): List<SubscriptionPlan>

  /**
   * Initiates the subscription flow for the authenticated user. The flow might vary with payment
   * providers. It creates a new `incomplete` subscription entity. On success, it returns HTTP 201
   * with a response body.
   *
   * To conclude this flow and transition the subscription entity to `active` state:
   *  - for Google Play plans, the clients must link `subscriptionId` with the subscription purchase
   *    by specifying it as `obfuscatedProfileId` in Google Play billing flow params.
   *  - for Stripe plans, the clients must redirect the user to the provided url to make the payment
   *   and complete the checkout session.
   *
   * Responses:
   *  - 201: subscription flow successfully initiated.
   *  - 400: request is not valid.
   *  - 401: access token is invalid.
   *  - 409: user already has an active subscription.
   *  - 500: internal server error.
   *
   * @param params `successUrl` and `cancelUrl` are only required for Stripe plans.
   * @return a [SubscriptionFlowResult] containing a new subscription entity and an optional stripe
   * checkout session url for subscriptions created with Stripe plans.
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  @NeedsAccessToken
  @POST("/v1/subscriptions")
  suspend fun create(@Body params: SubscriptionFlowParams): SubscriptionFlowResult
}

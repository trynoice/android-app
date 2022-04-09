package com.trynoice.api.client.apis

import com.trynoice.api.client.auth.annotations.NeedsAccessToken
import com.trynoice.api.client.models.Subscription
import com.trynoice.api.client.models.SubscriptionFlowParams
import com.trynoice.api.client.models.SubscriptionFlowResult
import com.trynoice.api.client.models.SubscriptionPlan
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
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
   *    by specifying it as `obfuscatedAccountId` in Google Play billing flow params.
   *  - for Stripe plans, the clients must redirect the user to the provided url to make the payment
   *   and complete the checkout session.
   *
   * Since clients may desire to use created subscription's id in `successUrl` and `cancelUrl`
   * callbacks, the server makes it available through `{subscriptionId}` template string in their
   * values, e.g. `https://api.test/success?id={subscriptionId}`. The server will replace the
   * template with the created subscription's id before creating a Stripe checkout session, i.e. it
   * will transform the previous url to `https://api.test/success?id=1`, assuming the created
   * subscription's id is `1`.
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

  /**
   * Lists a [page] of subscriptions purchased by the authenticated user. Each [page] contains
   * at-most 20 entries. If [onlyActive] is `true`, it lists the currently active subscription
   * purchase (at most one). It doesn't return subscription entities that were initiated, but were
   * never started.
   *
   * Responses:
   *  - 200: a page of subscription purchases.
   *  - 400: request is not valid.
   *  - 401: access token is invalid.
   *  - 404: the requested page number is higher than available.
   *  - 500: internal server error.
   *
   * @param onlyActive return only the active subscription (single instance).
   * @param stripeReturnUrl redirect URL for exiting Stripe customer portal.
   * @param page 0-indexed page number. Causes `HTTP 404` on exceeding the available limit.
   * @return a page of subscription purchases.
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  @NeedsAccessToken
  @GET("/v1/subscriptions")
  suspend fun list(
    @Query("onlyActive") onlyActive: Boolean = false,
    @Query("page") page: Int = 0,
    @Query("stripeReturnUrl") stripeReturnUrl: String? = null
  ): List<Subscription>

  /**
   * Get a subscription purchased by the authenticated user by its [subscriptionId]. It doesn't
   * return subscription entities that were initiated, but were never started.
   *
   * Responses:
   *  - 200: the requested subscription.
   *  - 400: request is not valid.
   *  - 401: access token is invalid.
   *  - 404: subscription with given id doesn't exist.
   *  - 500: internal server error.
   *
   * @param subscriptionId id of the subscription entity.
   * @param stripeReturnUrl optional redirect URL for exiting Stripe customer portal. (optional)
   * @return the requested [Subscription] entity.
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  @NeedsAccessToken
  @GET("/v1/subscriptions/{subscriptionId}")
  suspend fun get(
    @Path("subscriptionId") subscriptionId: Long,
    @Query("stripeReturnUrl") stripeReturnUrl: String? = null
  ): Subscription

  /**
   * Cancels the given subscription by requesting its cancellation from its provider. All providers
   * are configured to cancel subscriptions at the end of their current billing cycles. Returns
   * `HTTP 404` if an auth user doesn't have an active subscription.
   *
   * Responses:
   *  - 204: subscription cancelled.
   *  - 400: request is not valid.
   *  - 401: access token is invalid.
   *  - 404: no such active subscription exists that is owned by the auth user.
   *  - 500: internal server error.
   *
   * @param subscriptionId id of the subscription to cancel.
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  @NeedsAccessToken
  @DELETE("/v1/subscriptions/{subscriptionId}")
  suspend fun cancel(@Path("subscriptionId") subscriptionId: Long)
}

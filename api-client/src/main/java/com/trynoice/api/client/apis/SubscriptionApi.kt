package com.trynoice.api.client.apis

import com.trynoice.api.client.annotations.InjectAccessToken
import com.trynoice.api.client.models.GiftCard
import com.trynoice.api.client.models.StripeCustomerPortalUrlResponse
import com.trynoice.api.client.models.Subscription
import com.trynoice.api.client.models.SubscriptionFlowParams
import com.trynoice.api.client.models.SubscriptionFlowResponse
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
   * @param currency optional ISO 4217 currency code for including converted prices with plans.
   * @return [List]<[SubscriptionPlan]>
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  @GET("/v1/subscriptions/plans")
  suspend fun listPlans(
    @Query("provider") provider: String? = null,
    @Query("currency") currency: String? = null,
  ): List<SubscriptionPlan>

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
   * values, e.g. `https://api.test/success?id=%7BsubscriptionId%7D`. The server will replace the
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
   * @return a [SubscriptionFlowResponse] containing id of the new subscription entity and an
   * optional stripe checkout session url for subscriptions created with Stripe plans.
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  @InjectAccessToken
  @POST("/v2/subscriptions")
  suspend fun create(@Body params: SubscriptionFlowParams): SubscriptionFlowResponse

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
   *  - 500: internal server error.
   *
   * @param onlyActive return only the active subscription (single instance).
   * @param page 0-indexed page number.
   * @param currency optional ISO 4217 currency code for including converted prices with the
   * subscription's plan.
   * @return a list of subscription purchases; empty list if the page number is higher than
   * available data.
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  @InjectAccessToken
  @GET("/v2/subscriptions")
  suspend fun list(
    @Query("onlyActive") onlyActive: Boolean = false,
    @Query("page") page: Int = 0,
    @Query("currency") currency: String? = null,
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
   * @param currency optional ISO 4217 currency code for including converted prices with the
   * subscription's plan.
   * @return the requested [Subscription] entity.
   * @throws retrofit2.HttpException on API error.
   * @throws java.io.IOException on network error.
   */
  @InjectAccessToken
  @GET("/v2/subscriptions/{subscriptionId}")
  suspend fun get(
    @Path("subscriptionId") subscriptionId: Long,
    @Query("currency") currency: String? = null,
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
  @InjectAccessToken
  @DELETE("/v1/subscriptions/{subscriptionId}")
  suspend fun cancel(@Path("subscriptionId") subscriptionId: Long)

  /**
   * Retrieves an issued gift card.
   *
   * Responses:
   *  - 200: the requested gift card.
   *  - 400: request is not valid.
   *  - 401: access token is invalid.
   *  - 404: gift card doesn't exist.
   *  - 500: internal server error.
   *
   * @param code must not be blank.
   * @return the requested gift card.
   */
  @InjectAccessToken
  @GET("/v1/subscriptions/giftCards/{code}")
  suspend fun getGiftCard(@Path("code") code: String): GiftCard

  /**
   * Redeems an issued gift card and creates a subscription for the authenticated user.
   *
   * Responses:
   *  - 201: gift card redemption successful.
   *  - 400: request is not valid.
   *  - 401: access token is invalid.
   *  - 404: gift card doesn't exist.
   *  - 409: user already has an active subscription.
   *  - 410: gift card has expired.
   *  - 422: gift card has already been redeemed.
   *  - 500: internal server error.
   *
   * @param code must not be blank.
   */
  @InjectAccessToken
  @POST("/v2/subscriptions/giftCards/{code}/redeem")
  suspend fun redeemGiftCard(@Path("code") code: String)

  /**
   * Get the Stripe Customer Portal URL to allow customer to manage their subscription purchases.
   *
   * Responses:
   *  - 200: a response containing the Stripe Customer Portal URL.
   *  - 400: request is not valid.
   *  - 401: access token is invalid.
   *  - 404: customer isn't associated with Stripe.
   *  - 500: internal server error.
   *
   * @param returnUrl a not blank redirect URL for exiting the Stripe customer portal.
   * @return a response containing the Stripe Customer Portal URL.
   */
  @InjectAccessToken
  @GET("/v1/subscriptions/stripe/customerPortalUrl")
  suspend fun stripeCustomerPortalUrl(@Query("returnUrl") returnUrl: String): StripeCustomerPortalUrlResponse
}

package com.trynoice.api.client.apis

import com.trynoice.api.client.models.SubscriptionPlan
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * APIs related to subscription management.
 */
interface SubscriptionApi {

  /**
   * Lists all available subscription plans that an auth user can subscribe.
   *
   * The `provider` parameter (case-insensitive) can have the following values.
   * - empty or `null`: the response contains all available plans.
   * - `google_play`: the response contains all available plans supported by Google Play.
   * - `stripe`: the response contains all available plans supported by Stripe.
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
}

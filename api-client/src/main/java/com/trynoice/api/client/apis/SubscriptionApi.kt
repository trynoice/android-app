package com.trynoice.api.client.apis

import com.trynoice.api.client.models.SubscriptionPlan
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API Endpoints related to subscription management.
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
   *  - 200: a list of available subscription plans
   *  - 400: request is not valid
   *  - 422: the requested provider is not supported
   *  - 500: internal server error
   *
   * @param provider filter listed plans by the given provider. (optional)
   * @return [kotlin.collections.List<SubscriptionPlan>]
   */
  @GET("/v1/subscriptions/plans")
  suspend fun getPlans(@Query("provider") provider: String? = null): Response<List<SubscriptionPlan>>
}

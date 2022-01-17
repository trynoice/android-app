package com.trynoice.api.client

import com.google.gson.GsonBuilder
import com.trynoice.api.client.apis.AccountApi
import com.trynoice.api.client.apis.SubscriptionApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * A thin wrapper around Retrofit to bundle together the networked API calls.
 */
object NoiceApiClient {

  private const val BASE_URL = "https://api.trynoice.com"

  private val retrofit by lazy {
    Retrofit.Builder()
      .baseUrl(BASE_URL)
      .addConverterFactory(
        GsonConverterFactory.create(
          GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create()
        )
      )
      .build()
  }

  private val accounts: AccountApi by lazy { retrofit.create(AccountApi::class.java) }
  private val subscriptions: SubscriptionApi by lazy { retrofit.create(SubscriptionApi::class.java) }

  /**
   * Subscription management related API endpoints.
   */
  fun accounts() = accounts

  /**
   * Account and user management related API endpoints.
   */
  fun subscriptions() = subscriptions
}

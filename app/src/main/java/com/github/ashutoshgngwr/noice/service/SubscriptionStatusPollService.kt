package com.github.ashutoshgngwr.noice.service

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.Subscription
import dagger.hilt.android.AndroidEntryPoint
import io.github.ashutoshgngwr.may.May
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*
import javax.inject.Inject
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@AndroidEntryPoint
class SubscriptionStatusPollService : LifecycleService() {

  @set:Inject
  internal lateinit var apiClient: NoiceApiClient

  @set:Inject
  internal lateinit var cacheStore: May

  private val activeSubscription = MutableStateFlow<Subscription?>(null)
  private val serviceBinder = SubscriptionStatusPollServiceBinder(activeSubscription)

  override fun onBind(intent: Intent): IBinder {
    super.onBind(intent)
    return serviceBinder
  }

  override fun onCreate() {
    super.onCreate()
    activeSubscription.value = getCachedActiveSubscription()
    lifecycleScope.launch(Dispatchers.IO) {
      while (true) {
        val isSignedIn = apiClient.isSignedIn()
        val active = if (isSignedIn) getActiveSubscription() else null
        activeSubscription.emit(active)
        val pollDelay = computePollDelay(active?.renewsAt)
        Log.d(LOG_TAG, "scheduling poll after $pollDelay or sooner if sign-in state changes")
        // wait until timeout or signed-in state change
        withTimeoutOrNull(pollDelay) {
          apiClient.getSignedInState()
            .takeWhile { it == isSignedIn }
            .collect()
        }
      }
    }
  }

  private suspend fun getActiveSubscription(): Subscription? {
    return try {
      return apiClient.subscriptions().list(onlyActive = true)
        .firstOrNull()
        ?.also { cacheStore.put(ACTIVE_SUBSCRIPTION_KEY, it) } // cache latest value
    } catch (e: Throwable) {
      Log.i(LOG_TAG, "getActiveSubscription:", e)
      getCachedActiveSubscription() // return the cached value in case of an error
    }
  }

  private fun getCachedActiveSubscription(): Subscription? {
    return cacheStore.getAs<Subscription>(ACTIVE_SUBSCRIPTION_KEY)
      // only consider the cached value if the subscription hasn't expired.
      ?.takeIf { it.renewsAt?.after(Date()) ?: false }
  }

  private fun computePollDelay(renewsAt: Date?): Duration {
    // if subscription is active, next poll happens after five minutes. If the subscription is
    // expiring before 5 minutes, the next poll happens at the time of its expiration. if an
    // active subscription doesn't exist, next poll happens after 1 minute.
    if (renewsAt == null) {
      return 1.minutes
    }

    return min(5 * 60_000L, renewsAt.time - System.currentTimeMillis())
      .toDuration(DurationUnit.MILLISECONDS)
  }

  companion object {
    private const val LOG_TAG = "SubscriptionStatusPoll"
    private const val ACTIVE_SUBSCRIPTION_KEY = "subscriptionStatusPoll/activeSubscription"
  }
}

class SubscriptionStatusPollServiceBinder(activeSubscription: Flow<Subscription?>) : Binder() {
  val isSubscribed: Flow<Boolean> = activeSubscription.map { it != null }
}

package com.github.ashutoshgngwr.noice.provider

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import androidx.core.os.postDelayed
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryPurchasesAsync
import com.android.billingclient.api.querySkuDetails
import com.github.ashutoshgngwr.noice.ext.getMutableStringSet
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.SignatureException
import java.security.spec.X509EncodedKeySpec
import kotlin.math.min

/**
 * [RealInAppBillingProvider] implements a thin wrapper around Google Play Billing v4 client
 * library. It ensures that purchase notifications are delivered at-least once by checking if a
 * [InAppBillingProvider.PurchaseListener] is registered. When the listener is not registered, it
 * holds the notification delivery until it is registered. Therefore, clients must unregister their
 * listener when the UI is not visible to the users. Any pending notifications are delivered as soon
 * as a listener is registered. The clients will receive all listener callbacks on the main thread.
 */
class RealInAppBillingProvider(
  context: Context,
  private val defaultScope: CoroutineScope,
) : BillingClientStateListener, PurchasesUpdatedListener, InAppBillingProvider {

  private var purchaseListener: InAppBillingProvider.PurchaseListener? = null
  private var reconnectDelayMillis = RECONNECT_DELAY_START_MILLIS

  private val securityKey: PublicKey
  private val client: BillingClient
  private val handler = Handler(Looper.getMainLooper())
  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val gson = GsonBuilder().create()

  init {
    val decodedKey = Base64.decode(GOOGLE_PLAY_SECURITY_KEY, Base64.DEFAULT)
    securityKey = KeyFactory.getInstance("RSA")
      .generatePublic(X509EncodedKeySpec(decodedKey))

    client = BillingClient.newBuilder(context)
      .enablePendingPurchases()
      .setListener(this)
      .build()

    client.startConnection(this)
  }

  override fun onBillingSetupFinished(result: BillingResult) {
    Log.i(LOG_TAG, "onBillingSetupFinished: ${result.responseCode} ${result.debugMessage}")
    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
      retryServiceConnectionWithExponentialBackoff()
      return
    }

    reconnectDelayMillis = RECONNECT_DELAY_START_MILLIS
    defaultScope.launch(Dispatchers.IO) { refreshPurchases() }
  }

  override fun onBillingServiceDisconnected() = retryServiceConnectionWithExponentialBackoff()

  override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
      Log.w(LOG_TAG, "onPurchasesUpdated: ${result.responseCode} ${result.debugMessage}")
      return
    }

    defaultScope.launch { processPurchases(purchases ?: emptyList()) }
  }

  override fun setPurchaseListener(listener: InAppBillingProvider.PurchaseListener?): Unit =
    synchronized(prefs) {
      purchaseListener = listener
      if (listener == null) {
        return
      }

      prefs.getStringSet(PREF_PENDING_NOTIFICATIONS, null)?.forEach { json ->
        val bpp = gson.fromJson(json, InAppBillingProvider.Purchase::class.java)
        if (bpp.purchaseState == Purchase.PurchaseState.PENDING) {
          handler.post { listener.onPending(bpp) }
        } else if (bpp.purchaseState == Purchase.PurchaseState.PURCHASED) {
          handler.post { listener.onComplete(bpp) }
        }
      }

      prefs.edit(commit = true) { remove(PREF_PENDING_NOTIFICATIONS) }
    }

  override suspend fun queryDetails(
    type: InAppBillingProvider.SkuType,
    skus: List<String>
  ): List<InAppBillingProvider.SkuDetails> {
    val p = SkuDetailsParams.newBuilder()
      .setSkusList(skus)
      .setType(type.value)
      .build()

    val result = client.querySkuDetails(p)
    if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
      throw inAppBillingProviderException(result.billingResult)
    }

    return result.skuDetailsList?.map {
      InAppBillingProvider.SkuDetails(it.price, it.priceAmountMicros, it.originalJson)
    } ?: throw InAppBillingProviderException("sku details list is null")
  }

  override fun purchase(
    activity: Activity,
    sku: InAppBillingProvider.SkuDetails,
    oldPurchaseToken: String?,
    obfuscatedAccountId: String?,
  ) {
    val paramsBuilder = BillingFlowParams.newBuilder()
      .setSkuDetails(SkuDetails(sku.originalJSON))

    oldPurchaseToken?.let {
      paramsBuilder.setSubscriptionUpdateParams(
        BillingFlowParams.SubscriptionUpdateParams.newBuilder()
          .setOldSkuPurchaseToken(it)
          .setReplaceSkusProrationMode(BillingFlowParams.ProrationMode.IMMEDIATE_WITH_TIME_PRORATION)
          .build()
      )
    }

    obfuscatedAccountId?.let { paramsBuilder.setObfuscatedAccountId(it) }
    val result = client.launchBillingFlow(activity, paramsBuilder.build())
    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
      throw inAppBillingProviderException(result)
    }
  }

  override suspend fun acknowledgePurchase(purchase: InAppBillingProvider.Purchase) {
    val result = client.acknowledgePurchase(
      AcknowledgePurchaseParams.newBuilder()
        .setPurchaseToken(purchase.purchaseToken)
        .build()
    )

    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
      throw inAppBillingProviderException(result)
    }
  }

  override suspend fun consumePurchase(purchase: InAppBillingProvider.Purchase) {
    val result = client.consumePurchase(
      ConsumeParams.newBuilder()
        .setPurchaseToken(purchase.purchaseToken)
        .build()
    )

    if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
      throw inAppBillingProviderException(result.billingResult)
    }
  }

  private suspend fun refreshPurchases() {
    try {
      processPurchases(
        queryPurchases(InAppBillingProvider.SkuType.INAPP)
          + queryPurchases(InAppBillingProvider.SkuType.SUBS),
        true
      )
    } catch (e: InAppBillingProviderException) {
      Log.e(LOG_TAG, "refreshPurchases: failed query purchases", e)
    }
  }

  private fun processPurchases(
    purchases: List<Purchase>,
    removeAbsenteeItemsFromInternalState: Boolean = false,
  ) = synchronized(prefs) {
    val pending = prefs.getMutableStringSet(PREF_PENDING_PURCHASES)
    val completed = prefs.getMutableStringSet(PREF_COMPLETED_PURCHASES)

    for (p in purchases) {
      when (p.purchaseState) {
        Purchase.PurchaseState.PURCHASED -> {
          if (!isVerifiedPurchase(p)) {
            Log.i(LOG_TAG, "processPurchases: signature verification failed sku=${p.skus}")
            continue
          }

          if (completed.add(p.purchaseToken)) {
            notifyPurchase(p)
          }
        }

        Purchase.PurchaseState.PENDING -> {
          if (pending.add(p.purchaseToken)) {
            notifyPurchase(p)
          }
        }

        else -> Log.i(LOG_TAG, "processPurchases: unspecified purchase state skus=${p.skus}")
      }
    }

    // remove absentee purchase tokens from our internal state to keep its size in check.
    val activePurchaseTokens = purchases.map { it.purchaseToken }.toSet()
    if (removeAbsenteeItemsFromInternalState) {
      pending.removeAll { !activePurchaseTokens.contains(it) }
      completed.removeAll { !activePurchaseTokens.contains(it) }
    }

    prefs.edit(commit = true) {
      putStringSet(PREF_PENDING_PURCHASES, pending)
      putStringSet(PREF_COMPLETED_PURCHASES, completed)
    }
  }

  private fun retryServiceConnectionWithExponentialBackoff() {
    handler.postDelayed(reconnectDelayMillis) { client.startConnection(this) }
    reconnectDelayMillis = min(2 * reconnectDelayMillis, RECONNECT_DELAY_MAX_MILLIS)
  }

  private suspend fun queryPurchases(type: InAppBillingProvider.SkuType): List<Purchase> {
    val result = client.queryPurchasesAsync(type.value)
    if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
      throw inAppBillingProviderException(result.billingResult)
    }

    return result.purchasesList
  }

  private fun isVerifiedPurchase(p: Purchase): Boolean {
    if (p.signature.isBlank() || p.originalJson.isBlank()) {
      return false
    }

    try {
      val signatureBytes = Base64.decode(p.signature, Base64.DEFAULT)
      val signatureAlgorithm = Signature.getInstance("SHA1withRSA")
      signatureAlgorithm.initVerify(securityKey)
      signatureAlgorithm.update(p.originalJson.toByteArray())

      if (signatureAlgorithm.verify(signatureBytes)) {
        return true
      }

      Log.i(LOG_TAG, "signature verification failed")
    } catch (e: IllegalArgumentException) {
      Log.e(LOG_TAG, "base64 decoding failed", e)
    } catch (e: SignatureException) {
      Log.e(LOG_TAG, "signature exception", e)
    }

    return false
  }

  private fun notifyPurchase(purchase: Purchase) {
    val bpp = InAppBillingProvider.Purchase(
      skus = purchase.skus,
      purchaseToken = purchase.purchaseToken,
      purchaseState = purchase.purchaseState,
      obfuscatedAccountId = purchase.accountIdentifiers?.obfuscatedAccountId,
      originalJSON = purchase.originalJson,
      signature = purchase.signature,
    )

    when {
      purchaseListener == null -> {
        val pendingNotifications = prefs.getMutableStringSet(PREF_PENDING_NOTIFICATIONS)
        pendingNotifications.add(gson.toJson(bpp))
        prefs.edit(commit = true) {
          putStringSet(PREF_PENDING_NOTIFICATIONS, pendingNotifications)
        }
      }

      purchase.purchaseState == Purchase.PurchaseState.PENDING -> {
        handler.post { purchaseListener?.onPending(bpp) }
      }

      purchase.purchaseState == Purchase.PurchaseState.PURCHASED -> {
        handler.post { purchaseListener?.onComplete(bpp) }
      }
    }
  }

  private fun inAppBillingProviderException(result: BillingResult): InAppBillingProviderException {
    return InAppBillingProviderException("${result.responseCode} ${result.debugMessage}")
  }

  companion object {
    private const val LOG_TAG = "InAppBillingProvider"
    private const val PREFS_NAME = "com.github.ashutoshgngwr.noice.billingprovider"
    private const val PREF_PENDING_PURCHASES = "pending_purchases"
    private const val PREF_COMPLETED_PURCHASES = "completed_purchases"
    private const val PREF_PENDING_NOTIFICATIONS = "pending_notifications"
    private const val RECONNECT_DELAY_START_MILLIS = 1 * 1000L
    private const val RECONNECT_DELAY_MAX_MILLIS = 15 * 60 * 1000L
    private const val GOOGLE_PLAY_SECURITY_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkx" +
      "R84yP6PFaAFqhgVtFzI4O1Ws/xFQjJOhKCCrnyfs59SXSLsSbWjdMikCB7xcJsSkhfsril5iK88UIKhBLUW3oe16L" +
      "O7YMOS6xeH0s0LdxHnF1rdwr0WtFc41CjGFIMWFxkB27jft97Ycp72cASjmU3K/Jc0a2Qo3thrpZlJN+vwRzveaVU" +
      "E73o/FyzdUqyu4e8IngKOXDqxkgz3XLmzTHsx6XmeLTQl1WFMhOi5FxoHD9AHVoyZzbkGCMdRJERvk+ziSXdGR9pR" +
      "UtGVkv+h5v3fwfVqBYZC28TAz5w6wSRDFvUnIp52nQH52vdHf1mrwHbeniMUJzV5GoBaspTqQIDAQAB"
  }
}

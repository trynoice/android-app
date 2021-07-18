package com.github.ashutoshgngwr.noice.provider

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.core.os.HandlerCompat
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.querySkuDetails
import com.github.ashutoshgngwr.noice.ext.getMutableStringSet
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.SignatureException
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException

/**
 * [RealBillingProvider] implements a thin wrapper around Google Play Billing v4 client library. It
 * auto-acknowledges new purchases. It also handles state transition of pending purchases.
 */
object RealBillingProvider : BillingProvider, BillingClientStateListener {

  private const val GOOGLE_PLAY_SECURITY_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkxR84" +
    "yP6PFaAFqhgVtFzI4O1Ws/xFQjJOhKCCrnyfs59SXSLsSbWjdMikCB7xcJsSkhfsril5iK88UIKhBLUW3oe16LO7YMOS" +
    "6xeH0s0LdxHnF1rdwr0WtFc41CjGFIMWFxkB27jft97Ycp72cASjmU3K/Jc0a2Qo3thrpZlJN+vwRzveaVUE73o/Fyzd" +
    "Uqyu4e8IngKOXDqxkgz3XLmzTHsx6XmeLTQl1WFMhOi5FxoHD9AHVoyZzbkGCMdRJERvk+ziSXdGR9pRUtGVkv+h5v3f" +
    "wfVqBYZC28TAz5w6wSRDFvUnIp52nQH52vdHf1mrwHbeniMUJzV5GoBaspTqQIDAQAB"

  @VisibleForTesting
  internal const val PREF_NAME = "RealBillingProviderPreferences"

  @VisibleForTesting
  internal const val PREF_UNCONSUMED_ORDERS = "unconsumed_orders"
  private const val PREF_PENDING_ORDERS = "pending_orders"

  private const val COROUTINE_SCOPE_NAME = "RealBillingProviderScope"
  private val LOG_TAG = this::class.simpleName

  private const val RECONNECT_DELAY_MS = 2L * 1000L + 500L
  private const val RECONNECT_MAX_COUNT = 10
  private val RECONNECT_CALLBACK_TOKEN = "${this::class.qualifiedName}.reconnectCallback"

  private lateinit var client: BillingClient
  private lateinit var prefs: SharedPreferences
  private lateinit var defaultScope: CoroutineScope

  private var purchaseListener: BillingProvider.PurchaseListener? = null
  private var reconnectCount = 0

  private val prefsMutex = Mutex()
  private val handler = Handler(Looper.getMainLooper())
  private val isConnecting = AtomicBoolean(false)
  private val securityKey: PublicKey

  init {
    val decodedKey = Base64.decode(GOOGLE_PLAY_SECURITY_KEY, Base64.DEFAULT)
    securityKey = KeyFactory.getInstance("RSA")
      .generatePublic(X509EncodedKeySpec(decodedKey))
  }


  override fun onBillingSetupFinished(result: BillingResult) {
    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
      Log.w(LOG_TAG, "billing service connection failed: ${result.debugMessage}")
      retryServiceConnection()
      return
    }

    Log.d(LOG_TAG, "billing service connected")
    isConnecting.set(false)
    reconnectCount = 0
    refreshPurchases()
  }

  override fun onBillingServiceDisconnected() = retryServiceConnection()

  /**
   * Implements service connection retry with constant delay [RECONNECT_DELAY_MS].
   */
  private fun retryServiceConnection() {
    if (reconnectCount >= RECONNECT_MAX_COUNT) {
      isConnecting.set(false)
      reconnectCount = 0
      return
    }

    HandlerCompat.postDelayed(
      handler, { client.startConnection(this) },
      RECONNECT_CALLBACK_TOKEN, RECONNECT_DELAY_MS,
    )

    reconnectCount++
  }

  override fun init(context: Context, listener: BillingProvider.PurchaseListener?) {
    purchaseListener = listener
    defaultScope = CoroutineScope(Job() + CoroutineName(COROUTINE_SCOPE_NAME))

    client = BillingClient.newBuilder(context)
      .enablePendingPurchases()
      .setListener { _, _ -> refreshPurchases() }
      .build()

    prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    Log.d(LOG_TAG, "init: startConnection")
    isConnecting.set(true)
    client.startConnection(this)
  }

  override fun close() {
    handler.removeCallbacksAndMessages(RECONNECT_CALLBACK_TOKEN)
    client.endConnection()
    defaultScope.cancel()
    Log.d(LOG_TAG, "close: end connection")
  }

  override fun consumePurchase(orderId: String) {
    defaultScope.launch(Dispatchers.IO) {
      prefsMutex.withLock {
        val orders = prefs.getMutableStringSet(PREF_UNCONSUMED_ORDERS)
        orders.add(orderId)
        prefs.edit(commit = true) { putStringSet(PREF_UNCONSUMED_ORDERS, orders) }
        Log.d(LOG_TAG, "consumePurchase: add to unconsumed orders")
      }

      refreshPurchases()
    }
  }

  private fun refreshPurchases() {
    defaultScope.launch(Dispatchers.IO) {
      waitForBillingClientConnection()

      if (!client.isReady) {
        Log.d(LOG_TAG, "refreshPurchases: client is not ready")
      } else {
        reconcilePurchases()
      }
    }
  }

  @VisibleForTesting
  internal suspend fun reconcilePurchases(verifyPurchases: Boolean = true) = prefsMutex.withLock {
    val purchases: List<Purchase>
    try {
      purchases = queryPurchases()
    } catch (e: QueryPurchasesException) {
      Log.w(LOG_TAG, "reconcilePurchases: query purchase failed", e)
      return
    }

    val pendingOrders = prefs.getMutableStringSet(PREF_PENDING_ORDERS)
    val unconsumedOrders = prefs.getMutableStringSet(PREF_UNCONSUMED_ORDERS)

    for (p in purchases) {
      if (verifyPurchases && !isVerifiedPurchase(p)) {
        continue
      }

      if (p.purchaseState == Purchase.PurchaseState.PENDING && pendingOrders.add(p.orderId)) {
        Log.d(LOG_TAG, "reconcilePurchases: new pending order skus=${p.skus}")
        defaultScope.launch(Dispatchers.Main) { purchaseListener?.onPending(p.skus) }
      }

      if (p.purchaseState == Purchase.PurchaseState.PURCHASED && !p.isAcknowledged) {
        pendingOrders.remove(p.orderId)
        Log.d(LOG_TAG, "reconcilePurchases: new purchased order skus=${p.skus}")
        try {
          acknowledgePurchase(p.purchaseToken)
          Log.d(LOG_TAG, "reconcilePurchases: acknowledged skus=${p.skus}")
          defaultScope.launch(Dispatchers.Main) {
            purchaseListener?.onComplete(p.skus, p.orderId)
          }
        } catch (e: AcknowledgePurchaseException) {
          Log.w(LOG_TAG, "reconcilePurchases: acknowledge purchase failed", e)
        }
      }

      // consume marked purchases.
      if (p.purchaseState == Purchase.PurchaseState.PURCHASED && p.orderId in unconsumedOrders) {
        try {
          consumePurchaseAsync(p.purchaseToken)
          unconsumedOrders.remove(p.orderId)
          Log.d(LOG_TAG, "reconcilePurchases: consumed skus=${p.skus}")
        } catch (e: ConsumePurchaseException) {
          Log.w(LOG_TAG, "reconcilePurchases: consume purchase failed", e)
        }
      }
    }

    prefs.edit(commit = true) {
      putStringSet(PREF_PENDING_ORDERS, pendingOrders)
      putStringSet(PREF_UNCONSUMED_ORDERS, unconsumedOrders)
    }
  }

  private suspend fun waitForBillingClientConnection() {
    while (isConnecting.get()) {
      delay(500)
    }
  }

  /**
   * Query all purchases.
   *
   * @return list of in-app and subscription purchases
   * @throws QueryPurchasesException when the query fails.
   */
  @Throws(QueryPurchasesException::class)
  private suspend fun queryPurchases(): List<Purchase> {
    return queryPurchases(BillingClient.SkuType.SUBS) + queryPurchases(BillingClient.SkuType.INAPP)
  }

  /**
   * Query purchases of the provided [BillingClient.SkuType] [type].
   *
   * @return list of purchases
   * @throws QueryPurchasesException when the query fails.
   */
  @Throws(QueryPurchasesException::class)
  private suspend fun queryPurchases(@BillingClient.SkuType type: String): List<Purchase> {
    return suspendCancellableCoroutine { c ->
      client.queryPurchasesAsync(type) { result, purchaseList ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
          c.resume(purchaseList) { e ->
            Log.d(LOG_TAG, "queryPurchases cancelled", e)
          }
        } else {
          c.resumeWithException(QueryPurchasesException(result.debugMessage))
        }
      }
    }
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

      Log.w(LOG_TAG, "Signature verification failed")
    } catch (e: IllegalArgumentException) {
      Log.w(LOG_TAG, "Base64 decoding failed", e)
    } catch (e: SignatureException) {
      Log.e(LOG_TAG, "Signature exception", e)
    }

    return false
  }

  /**
   * Acknowledge a new purchase.
   *
   * @throws AcknowledgePurchaseException when the acknowledge query fails.
   */
  @Throws(AcknowledgePurchaseException::class)
  private suspend fun acknowledgePurchase(token: String) {
    return suspendCancellableCoroutine { c ->
      val params = AcknowledgePurchaseParams.newBuilder()
        .setPurchaseToken(token)
        .build()

      client.acknowledgePurchase(params) { result ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
          c.resume(Unit) { e ->
            Log.d(LOG_TAG, "acknowledgePurchase cancelled", e)
          }
        } else {
          c.resumeWithException(AcknowledgePurchaseException(result.debugMessage))
        }
      }
    }
  }

  /**
   * Consume an in-app purchase.
   *
   * @throws ConsumePurchaseException when the consume query fails.
   */
  @Throws(ConsumePurchaseException::class)
  private suspend fun consumePurchaseAsync(token: String) {
    val r = client.consumePurchase(
      ConsumeParams.newBuilder()
        .setPurchaseToken(token)
        .build()
    )

    if (r.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
      throw ConsumePurchaseException(r.billingResult.debugMessage)
    }
  }

  /**
   * Query details of given [skus].
   *
   * @return List of [SkuDetails] for provided [skus].
   * @throws ConsumePurchaseException when the consume query fails.
   */
  @Throws(QueryDetailsException::class)
  suspend fun queryDetails(
    @BillingClient.SkuType type: String,
    skus: List<String>
  ): List<SkuDetails> {
    waitForBillingClientConnection()
    if (!client.isReady) {
      throw QueryDetailsException("billing client is not ready")
    }

    val p = SkuDetailsParams.newBuilder()
      .setSkusList(skus)
      .setType(type)
      .build()

    val r = client.querySkuDetails(p)
    if (r.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
      throw QueryDetailsException(r.billingResult.debugMessage)
    }

    return r.skuDetailsList ?: throw QueryDetailsException("sku details list is null")
  }

  /**
   * Launch billing flow for the given [sku].
   */
  fun purchase(activity: Activity, sku: SkuDetails): Boolean {
    val params = BillingFlowParams.newBuilder()
      .setSkuDetails(sku)
      .build()

    val result = client.launchBillingFlow(activity, params)
    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
      Log.w(LOG_TAG, "failed to start billing flow: ${result.debugMessage}")
      return false
    }

    return true
  }

  class QueryDetailsException(msg: String) : Exception(msg)
  class QueryPurchasesException(msg: String) : Exception(msg)
  class AcknowledgePurchaseException(msg: String) : Exception(msg)
  class ConsumePurchaseException(msg: String) : Exception(msg)
}

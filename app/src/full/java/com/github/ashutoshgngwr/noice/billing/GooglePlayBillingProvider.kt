package com.github.ashutoshgngwr.noice.billing

import android.app.Activity
import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.room.withTransaction
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.Purchase.PurchaseState
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.github.ashutoshgngwr.noice.AppDispatchers
import com.github.ashutoshgngwr.noice.data.AppDatabase
import com.github.ashutoshgngwr.noice.data.models.GooglePlayInAppPurchaseDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.SignatureException
import java.security.spec.X509EncodedKeySpec
import kotlin.math.min

/**
 * [GooglePlayBillingProvider] implements a thin wrapper around Google Play Billing client library.
 * It ensures that purchase notifications are delivered at-least once by ensuring that at least one
 * [GooglePlayPurchaseListener] consumes a notification. If no listeners consume a notification, it
 * is delivered for all newly registered listeners until one of them consumes it. The clients
 * receive all listener callbacks on the main thread.
 */
class GooglePlayBillingProvider(
  @ApplicationContext context: Context,
  private val appDb: AppDatabase,
  private val defaultScope: CoroutineScope,
  private val appDispatchers: AppDispatchers,
) : BillingClientStateListener, PurchasesUpdatedListener {

  private var reconnectDelayMillis = RECONNECT_DELAY_START_MILLIS
  private var reconnectJob: Job? = null

  private val securityKey: PublicKey
  private val client: BillingClient
  private val purchaseListeners = mutableSetOf<GooglePlayPurchaseListener>()

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
    refreshPurchases()
  }

  override fun onBillingServiceDisconnected() = retryServiceConnectionWithExponentialBackoff()

  override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
      Log.w(LOG_TAG, "onPurchasesUpdated: ${result.responseCode} ${result.debugMessage}")
      return
    }

    defaultScope.launch(appDispatchers.io) { processPurchases(purchases ?: emptyList(), false) }
  }

  /**
   * Registers a purchase listener.
   */
  fun addPurchaseListener(listener: GooglePlayPurchaseListener) {
    if (!purchaseListeners.add(listener)) { // listener was already present.
      return
    }

    notifyListeners()
  }

  /**
   * Removes a previously registered purchase listener.
   */
  fun removePurchaseListener(listener: GooglePlayPurchaseListener) {
    purchaseListeners.remove(listener)
  }

  /**
   * Query details of the given [productIds].
   *
   * @return a list of [ProductDetails] for provided [productIds].
   * @throws GooglePlayBillingProviderException when the query fails.
   */
  suspend fun queryDetails(
    @ProductType type: String,
    productIds: List<String>,
  ): List<ProductDetails> {
    val result = QueryProductDetailsParams.newBuilder()
      .setProductList(productIds.map { productId ->
        QueryProductDetailsParams.Product.newBuilder()
          .setProductId(productId)
          .setProductType(type)
          .build()
      })
      .build()
      .let { client.queryProductDetails(it) }

    if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
      throw buildBillingProviderException(result.billingResult)
    }

    return result.productDetailsList
      ?: throw GooglePlayBillingProviderException("product details list is null")
  }

  /**
   * Starts purchase flow for the given product [details].
   *
   * @param activity current activity context to launch the billing flow.
   * @param details [ProductDetails] of the in-app product or subscription to purchase.
   * @param subscriptionOfferToken selected offer token for subscription products.
   * @param oldPurchaseToken purchase token of the active subscription to launch an upgrade flow.
   * @param obfuscatedAccountId an identifier to identify purchase on the server-side.
   * @throws GooglePlayBillingProviderException on failing to launch the billing flow.
   */
  fun purchase(
    activity: Activity,
    details: ProductDetails,
    subscriptionOfferToken: String? = null,
    oldPurchaseToken: String? = null,
    obfuscatedAccountId: String? = null,
  ) {
    val result = BillingFlowParams.newBuilder()
      .setIsOfferPersonalized(false)
      .setProductDetailsParamsList(
        listOf(
          BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .also { b -> subscriptionOfferToken?.also { b.setOfferToken(it) } }
            .build()
        )
      )
      .also { b ->
        oldPurchaseToken?.also { token ->
          b.setSubscriptionUpdateParams(
            BillingFlowParams.SubscriptionUpdateParams.newBuilder()
              .setOldPurchaseToken(token)
              .setSubscriptionReplacementMode(BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION)
              .build()
          )
        }
      }
      .also { b -> obfuscatedAccountId?.also { b.setObfuscatedAccountId(it) } }
      .build()
      .let { client.launchBillingFlow(activity, it) }

    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
      throw buildBillingProviderException(result)
    }
  }

  /**
   * Consumes a given purchase.
   *
   * @throws GooglePlayBillingProviderException on failing to consume the purchase.
   */
  suspend fun consumePurchase(purchase: Purchase) {
    val result = client.consumePurchase(
      ConsumeParams.newBuilder()
        .setPurchaseToken(purchase.purchaseToken)
        .build()
    )

    if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
      throw buildBillingProviderException(result.billingResult)
    }
  }

  private fun refreshPurchases() = defaultScope.launch(appDispatchers.io) {
    try {
      processPurchases(queryPurchases(ProductType.INAPP) + queryPurchases(ProductType.SUBS), true)
    } catch (e: GooglePlayBillingProviderException) {
      Log.e(LOG_TAG, "refreshPurchases: failed query purchases", e)
    }
  }

  private suspend fun processPurchases(
    purchases: List<Purchase>,
    removeAbsenteeItemsFromInternalState: Boolean,
  ) {
    appDb.withTransaction {
      purchases.forEach { p ->
        if (p.purchaseState == PurchaseState.PURCHASED && !isVerifiedPurchase(p)) {
          Log.i(LOG_TAG, "processPurchases: signature verification failed products=${p.products}")
          return@forEach
        }

        if (p.purchaseState == PurchaseState.UNSPECIFIED_STATE) {
          Log.i(LOG_TAG, "processPurchases: unspecified purchase state, products=${p.products}")
          return@forEach
        }

        val dto = appDb.googlePlayInAppPurchases()
          .getByPurchaseToken(p.purchaseToken)
          ?.let { oldDto ->
            val oldP = Purchase(oldDto.purchaseInfoJson, oldDto.signature)
            oldDto.copy(
              purchaseToken = p.purchaseToken,
              purchaseInfoJson = p.originalJson,
              signature = p.signature,
              // when the purchase state changes, notification needs to be redelivered.
              isNotificationConsumed = oldDto.isNotificationConsumed && oldP.purchaseState == p.purchaseState
            )
          }
          ?: GooglePlayInAppPurchaseDto(
            purchaseToken = p.purchaseToken,
            purchaseInfoJson = p.originalJson,
            signature = p.signature,
            isNotificationConsumed = false,
          )

        appDb.googlePlayInAppPurchases().save(dto)
      }

      // remove absentee purchase tokens from our internal state to keep its size in check.
      if (removeAbsenteeItemsFromInternalState) {
        val activePurchaseTokens = purchases.map { it.purchaseToken }.toSet()
        appDb.googlePlayInAppPurchases()
          .listPurchaseTokensWithConsumedNotifications()
          .filterNot { it in activePurchaseTokens }
          .forEach { appDb.googlePlayInAppPurchases().deleteByPurchaseToken(it) }
      }
    }

    notifyListeners()
  }

  private fun retryServiceConnectionWithExponentialBackoff() {
    reconnectJob?.cancel()
    reconnectJob = defaultScope.launch(appDispatchers.main) {
      delay(reconnectDelayMillis)
      reconnectDelayMillis = min(2 * reconnectDelayMillis, RECONNECT_DELAY_MAX_MILLIS)
      try {
        // throws `java.lang.IllegalStateException: Too many bind requests(999+) for service Intent`.
        client.startConnection(this@GooglePlayBillingProvider)
      } catch (e: IllegalStateException) {
        Log.w(LOG_TAG, "retryServiceConnectionWithExponentialBackoff: request failed", e)
      }
    }
  }

  private suspend fun queryPurchases(@ProductType type: String): List<Purchase> {
    val result = QueryPurchasesParams.newBuilder()
      .setProductType(type)
      .build()
      .let { client.queryPurchasesAsync(it) }

    if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
      throw buildBillingProviderException(result.billingResult)
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

  private fun notifyListeners() = defaultScope.launch(appDispatchers.io) {
    if (purchaseListeners.isEmpty()) {
      return@launch
    }

    appDb.withTransaction {
      appDb.googlePlayInAppPurchases()
        .listWithUnconsumedNotifications()
        .forEach { dto ->
          val purchase = Purchase(dto.purchaseInfoJson, dto.signature)
          val isNotificationConsumed = withContext(appDispatchers.main) {
            when (purchase.purchaseState) {
              PurchaseState.PENDING -> purchaseListeners.any { it.onInAppPurchasePending(purchase) }
              PurchaseState.PURCHASED -> purchaseListeners.any { it.onInAppPurchaseComplete(purchase) }
              else -> true
            }
          }

          appDb.googlePlayInAppPurchases()
            .save(dto.copy(isNotificationConsumed = isNotificationConsumed))
        }
    }
  }

  private fun buildBillingProviderException(result: BillingResult): GooglePlayBillingProviderException {
    return GooglePlayBillingProviderException("${result.responseCode} ${result.debugMessage}")
  }

  companion object {
    private const val LOG_TAG = "GooglePlayInAppBilling"
    private const val RECONNECT_DELAY_START_MILLIS = 1 * 1000L
    private const val RECONNECT_DELAY_MAX_MILLIS = 15 * 60 * 1000L
    private const val GOOGLE_PLAY_SECURITY_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkx" +
      "R84yP6PFaAFqhgVtFzI4O1Ws/xFQjJOhKCCrnyfs59SXSLsSbWjdMikCB7xcJsSkhfsril5iK88UIKhBLUW3oe16L" +
      "O7YMOS6xeH0s0LdxHnF1rdwr0WtFc41CjGFIMWFxkB27jft97Ycp72cASjmU3K/Jc0a2Qo3thrpZlJN+vwRzveaVU" +
      "E73o/FyzdUqyu4e8IngKOXDqxkgz3XLmzTHsx6XmeLTQl1WFMhOi5FxoHD9AHVoyZzbkGCMdRJERvk+ziSXdGR9pR" +
      "UtGVkv+h5v3fwfVqBYZC28TAz5w6wSRDFvUnIp52nQH52vdHf1mrwHbeniMUJzV5GoBaspTqQIDAQAB"
  }
}

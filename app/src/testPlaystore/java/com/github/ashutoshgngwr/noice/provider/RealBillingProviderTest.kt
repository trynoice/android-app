package com.github.ashutoshgngwr.noice.provider

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ConsumeResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.github.ashutoshgngwr.noice.ext.getMutableStringSet
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class RealBillingProviderTest {

  private lateinit var mockBillingClient: BillingClient
  private lateinit var mockListener: BillingProvider.PurchaseListener
  private lateinit var billingProvider: RealBillingProvider

  @Before
  fun setup() {
    mockBillingClient = mockk(relaxed = true) {
      every { startConnection(any()) } answers {
        firstArg<BillingClientStateListener>().onBillingSetupFinished(
          newBillingResult(BillingClient.BillingResponseCode.OK)
        )
      }
    }

    mockkStatic(BillingClient::class)
    every { BillingClient.newBuilder(any()) } returns mockk(relaxed = true) {
      every { enablePendingPurchases() } returns this
      every { setListener(any()) } returns this
      every { build() } returns mockBillingClient
    }

    mockListener = mockk(relaxed = true)
    billingProvider = RealBillingProvider
    billingProvider.init(ApplicationProvider.getApplicationContext(), mockListener)
  }

  @After
  fun teardown() {
    billingProvider.close() // cancels its internal coroutine scope.
  }

  @Test
  fun testInit() {
    val listenerSlot = slot<BillingClientStateListener>()
    verify { mockBillingClient.startConnection(capture(listenerSlot)) }
    assertTrue(listenerSlot.isCaptured)

    clearMocks(mockBillingClient)
    listenerSlot.captured.onBillingSetupFinished(
      newBillingResult(BillingClient.BillingResponseCode.ERROR)
    )

    verify(exactly = 0) { mockBillingClient.startConnection(any()) }
    ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    verify(exactly = 1) { mockBillingClient.startConnection(any()) }

    clearMocks(mockBillingClient)
    listenerSlot.captured.onBillingSetupFinished(
      newBillingResult(BillingClient.BillingResponseCode.OK)
    )

    ShadowLooper.runMainLooperToNextTask()
    verify(exactly = 0) { mockBillingClient.startConnection(any()) }
  }

  @Test
  fun testClose() {
    billingProvider.close()
    verify(exactly = 1) { mockBillingClient.endConnection() }
  }

  @Test
  fun testReconcilePurchases() = runBlockingTest {
    val pendingSkuList = arrayListOf("test-sku-1", "test-sku-2")
    val mockInAppPurchase1 = mockk<Purchase>(relaxed = true) {
      every { orderId } returns "test-order-id-1"
      every { purchaseState } returns Purchase.PurchaseState.PENDING
      every { purchaseToken } returns "test-purchase-token-1"
      every { skus } returns pendingSkuList
      every { isAcknowledged } returns false
    }

    val purchasedSkuList = arrayListOf("test-sku-3", "test-sku-4")
    val mockInAppPurchase2 = mockk<Purchase>(relaxed = true) {
      every { orderId } returns "test-order-id-2"
      every { purchaseState } returns Purchase.PurchaseState.PURCHASED
      every { purchaseToken } returns "test-purchase-token-2"
      every { skus } returns purchasedSkuList
      every { isAcknowledged } returns false
    }

    val acknowledgedSkuList = arrayListOf("test-sku-5", "test-sku-6")
    val unconsumedOrderId = "test-order-id-3"
    val unconsumedPurchaseToken = "test-purchase-token-3"
    val mockInAppPurchase3 = mockk<Purchase>(relaxed = true) {
      every { orderId } returns unconsumedOrderId
      every { purchaseState } returns Purchase.PurchaseState.PURCHASED
      every { purchaseToken } returns unconsumedPurchaseToken
      every { skus } returns acknowledgedSkuList
      every { isAcknowledged } returns true
    }

    every {
      mockBillingClient.queryPurchasesAsync(any(), any())
    } answers {
      val purchaseList = mutableListOf<Purchase>()
      if (firstArg<String>() == BillingClient.SkuType.INAPP) {
        purchaseList.add(mockInAppPurchase1)
        purchaseList.add(mockInAppPurchase2)
        purchaseList.add(mockInAppPurchase3)
      }

      secondArg<PurchasesResponseListener>().onQueryPurchasesResponse(
        newBillingResult(BillingClient.BillingResponseCode.OK),
        purchaseList
      )
    }

    every { mockBillingClient.acknowledgePurchase(any(), any()) } answers {
      secondArg<AcknowledgePurchaseResponseListener>().onAcknowledgePurchaseResponse(
        newBillingResult(BillingClient.BillingResponseCode.OK),
      )
    }

    every { mockBillingClient.consumeAsync(any(), any()) } answers {
      secondArg<ConsumeResponseListener>().onConsumeResponse(
        newBillingResult(BillingClient.BillingResponseCode.OK),
        firstArg<ConsumeParams>().purchaseToken,
      )
    }

    ApplicationProvider.getApplicationContext<Context>()
      .getSharedPreferences(RealBillingProvider.PREF_NAME, Context.MODE_PRIVATE)
      .edit { putStringSet(RealBillingProvider.PREF_UNCONSUMED_ORDERS, setOf(unconsumedOrderId)) }

    billingProvider.reconcilePurchases(verifyPurchases = false)
    ShadowLooper.idleMainLooper()

    val pendingSkuListSlot = slot<List<String>>()
    val purchasedSkuListSlot = slot<List<String>>()
    val consumeParamsSlot = slot<ConsumeParams>()
    verify(exactly = 1) {
      mockListener.onPending(capture(pendingSkuListSlot))
      mockListener.onComplete(capture(purchasedSkuListSlot), "test-order-id-2")
      mockBillingClient.consumeAsync(capture(consumeParamsSlot), any())
    }

    assertTrue(consumeParamsSlot.isCaptured)
    assertEquals(unconsumedPurchaseToken, consumeParamsSlot.captured.purchaseToken)
  }

  @Test
  fun testConsumePurchase() {
    val orderID = "test-order-id"
    val mockPurchase = mockk<Purchase>(relaxed = true) {
      every { orderId } returns orderID
      every { purchaseState } returns Purchase.PurchaseState.PURCHASED
      every { purchaseToken } returns "test-purchase-token"
      every { skus } returns arrayListOf("test-sku")
      every { isAcknowledged } returns true
    }

    every { mockBillingClient.isReady } returns true
    every { mockBillingClient.queryPurchasesAsync(BillingClient.SkuType.INAPP, any()) } answers {
      secondArg<PurchasesResponseListener>().onQueryPurchasesResponse(
        newBillingResult(BillingClient.BillingResponseCode.OK), listOf(mockPurchase),
      )
    }

    every { mockBillingClient.consumeAsync(any(), any()) } answers {
      secondArg<ConsumeResponseListener>().onConsumeResponse(
        newBillingResult(BillingClient.BillingResponseCode.OK),
        "test-purchase-token"
      )
    }

    billingProvider.consumePurchase(orderID)
    verify(timeout = 15000) { mockBillingClient.queryPurchasesAsync(any(), any()) }

    val unconsumedOrders = ApplicationProvider.getApplicationContext<Context>()
      .getSharedPreferences(RealBillingProvider.PREF_NAME, Context.MODE_PRIVATE)
      .getMutableStringSet(RealBillingProvider.PREF_UNCONSUMED_ORDERS)

    assertTrue(orderID in unconsumedOrders)
  }

  private fun newBillingResult(@BillingClient.BillingResponseCode code: Int): BillingResult {
    return BillingResult.newBuilder()
      .setResponseCode(code)
      .build()
  }
}

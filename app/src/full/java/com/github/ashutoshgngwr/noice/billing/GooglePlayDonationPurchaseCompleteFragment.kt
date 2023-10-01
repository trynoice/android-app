package com.github.ashutoshgngwr.noice.billing

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.Purchase
import com.github.ashutoshgngwr.noice.AppDispatchers
import com.github.ashutoshgngwr.noice.databinding.GooglePlayDonationPurchaseCompleteFragmentBinding
import com.github.ashutoshgngwr.noice.ext.launchAndRepeatOnStarted
import com.github.ashutoshgngwr.noice.metrics.AnalyticsProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GooglePlayDonationPurchaseCompleteFragment : BottomSheetDialogFragment() {

  @set:Inject
  internal var analyticsProvider: AnalyticsProvider? = null

  private lateinit var binding: GooglePlayDonationPurchaseCompleteFragmentBinding
  private val viewModel: GooglePlayDonationPurchaseCompleteViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = GooglePlayDonationPurchaseCompleteFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    binding.dismiss.setOnClickListener { dismiss() }
    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.isLoading.collect { isCancelable = !it }
    }

    val purchaseInfoJson = requireNotNull(arguments?.getString(PURCHASE_INFO_JSON))
    val purchaseSignature = requireNotNull(arguments?.getString(PURCHASE_SIGNATURE))
    viewModel.consumePurchase(Purchase(purchaseInfoJson, purchaseSignature))
    analyticsProvider?.setCurrentScreen(this::class)
  }

  companion object {
    private const val PURCHASE_INFO_JSON = "purchaseInfoJson"
    private const val PURCHASE_SIGNATURE = "purchaseSignature"

    fun newInstance(purchase: Purchase): GooglePlayDonationPurchaseCompleteFragment {
      return GooglePlayDonationPurchaseCompleteFragment().apply {
        arguments = bundleOf(
          PURCHASE_INFO_JSON to purchase.originalJson,
          PURCHASE_SIGNATURE to purchase.signature,
        )
      }
    }
  }
}

@HiltViewModel
class GooglePlayDonationPurchaseCompleteViewModel @Inject constructor(
  private val billingProvider: GooglePlayBillingProvider?,
  private val appDispatchers: AppDispatchers,
) : ViewModel() {

  val isLoading = MutableStateFlow(true)
  val error = MutableStateFlow<Throwable?>(null)

  fun consumePurchase(purchase: Purchase) = viewModelScope.launch(appDispatchers.main) {
    try {
      requireNotNull(billingProvider) { "GooglePlayBillingProvider must not be null" }
      billingProvider.consumePurchase(purchase)
    } catch (e: Throwable) {
      Log.w(LOG_TAG, "consumePurchase: failed to consume purchase", e)
      error.emit(e)
    } finally {
      isLoading.emit(false)
    }
  }

  companion object {
    private const val LOG_TAG = "GooglePlayDonationVM"
  }
}

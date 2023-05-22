package com.github.ashutoshgngwr.noice.billing

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.ProductDetails
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.GooglePlayDonateButtonsFragmentBinding
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GooglePlayDonateButtonsFragment : Fragment() {

  private lateinit var binding: GooglePlayDonateButtonsFragmentBinding
  private val viewModel: GooglePlayDonateButtonsViewModel by viewModels()

  @set:Inject
  internal var billingProvider: GooglePlayBillingProvider? = null

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = GooglePlayDonateButtonsFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    binding.donateButtonClickListener = GooglePlayDonateButtonClickListener { productDetails ->
      try {
        billingProvider?.purchase(requireActivity(), productDetails)
      } catch (e: GooglePlayBillingProviderException) {
        Log.w(LOG_TAG, "onViewCreated: failed to launch billing flow", e)
        showErrorSnackBar(R.string.failed_to_purchase)
      }
    }

    viewModel.loadProductDetails(IN_APP_DONATION_PRODUCT_SKUS)
  }

  companion object {
    val IN_APP_DONATION_PRODUCT_SKUS = listOf(
      "donate_usd1", "donate_usd2", "donate_usd5",
      "donate_usd10", "donate_usd15", "donate_usd25",
    )

    private const val LOG_TAG = "GooglePlayDonateF"
  }
}

@BindingAdapter("donationProductDetailsList", "donateButtonClickListener")
fun setDonationProductDetailsList(
  container: ViewGroup,
  productDetailsList: List<ProductDetails>,
  clickListener: GooglePlayDonateButtonClickListener,
) {
  val sorted = productDetailsList.sortedBy { it.oneTimePurchaseOfferDetails?.priceAmountMicros }
  if (container.tag == sorted) {
    return
  }

  container.tag = sorted
  container.removeAllViews()
  sorted.forEach { productDetails ->
    val b = MaterialButton(
      container.context,
      null,
      com.google.android.material.R.attr.materialButtonOutlinedStyle,
    )
    b.text = productDetails.oneTimePurchaseOfferDetails?.formattedPrice
    b.setOnClickListener { clickListener.onDonateButtonClicked(productDetails) }
    container.addView(b)
  }
}

@HiltViewModel
class GooglePlayDonateButtonsViewModel @Inject constructor(
  private val billingProvider: GooglePlayBillingProvider?,
) : ViewModel() {

  val productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
  val isLoading = MutableStateFlow(true)
  val error = MutableStateFlow<Throwable?>(null)

  fun loadProductDetails(skus: List<String>) = viewModelScope.launch {
    try {
      val details = billingProvider?.queryDetails(ProductType.INAPP, skus)
      requireNotNull(details) { "product details must not be null" }
      productDetails.emit(details)
    } catch (e: Throwable) {
      Log.w(LOG_TAG, "failed to get product details", e)
      error.emit(e)
    } finally {
      isLoading.emit(false)
    }
  }

  companion object {
    private const val LOG_TAG = "GooglePlayDonateVM"
  }
}

fun interface GooglePlayDonateButtonClickListener {
  fun onDonateButtonClicked(donationProductDetails: ProductDetails)
}

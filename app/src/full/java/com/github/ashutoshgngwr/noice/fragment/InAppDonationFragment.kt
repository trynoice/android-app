package com.github.ashutoshgngwr.noice.fragment

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
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.InAppDonationFragmentBinding
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.provider.DonationFragmentProvider
import com.github.ashutoshgngwr.noice.provider.InAppBillingProvider
import com.github.ashutoshgngwr.noice.provider.InAppBillingProviderException
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val LOG_TAG = "InAppDonationFragment"

@AndroidEntryPoint
class InAppDonationFragment : Fragment() {

  private lateinit var binding: InAppDonationFragmentBinding
  private val viewModel: InAppDonationViewModel by viewModels()

  @set:Inject
  internal lateinit var billingProvider: InAppBillingProvider

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = InAppDonationFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    binding.productSelectedListener = OnProductSelectedListener { productDetails ->
      try {
        billingProvider.purchase(requireActivity(), productDetails)
      } catch (e: InAppBillingProviderException) {
        Log.w(LOG_TAG, "onViewCreated: failed to launch billing flow", e)
        showErrorSnackBar(R.string.failed_to_purchase)
      }
    }
  }
}

@BindingAdapter("donationProductDetailsList", "onProductSelected")
fun setDonationProductDetailsList(
  container: ViewGroup,
  productDetailsList: List<InAppBillingProvider.ProductDetails>,
  selectedListener: OnProductSelectedListener,
) {
  val sorted = productDetailsList.sortedBy { it.oneTimeOfferDetails?.priceAmountMicros }
  if (container.tag == sorted) {
    return
  }

  container.tag = sorted
  container.removeAllViews()
  sorted.forEach { productDetails ->
    val b = MaterialButton(container.context, null, R.attr.materialButtonOutlinedStyle)
    b.text = productDetails.oneTimeOfferDetails?.price
    b.setOnClickListener { selectedListener.onProductSelected(productDetails) }
    container.addView(b)
  }
}

@HiltViewModel
class InAppDonationViewModel @Inject constructor(
  billingProvider: InAppBillingProvider,
) : ViewModel() {

  val productDetails = MutableStateFlow<List<InAppBillingProvider.ProductDetails>>(emptyList())
  val isLoading = MutableStateFlow(true)
  val error = MutableStateFlow<Throwable?>(null)

  init {
    viewModelScope.launch {
      try {
        val details = billingProvider.queryDetails(
          InAppBillingProvider.ProductType.INAPP,
          DonationFragmentProvider.IN_APP_DONATION_PRODUCTS,
        )

        productDetails.emit(details)
      } catch (e: InAppBillingProviderException) {
        Log.w(LOG_TAG, "failed to get product details", e)
        error.emit(e)
      } finally {
        isLoading.emit(false)
      }
    }
  }

  companion object {
    private const val LOG_TAG = "InAppDonationFragment"
  }
}

fun interface OnProductSelectedListener {
  fun onProductSelected(productDetails: InAppBillingProvider.ProductDetails)
}

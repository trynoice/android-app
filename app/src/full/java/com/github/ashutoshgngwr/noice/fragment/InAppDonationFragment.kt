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
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.provider.DonationFragmentProvider
import com.github.ashutoshgngwr.noice.provider.InAppBillingProvider
import com.github.ashutoshgngwr.noice.provider.InAppBillingProviderException
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
    viewModel.skuDetailsClickListener = OnSkuDetailsClickListener { skuDetails ->
      try {
        billingProvider.purchase(requireActivity(), skuDetails)
      } catch (e: InAppBillingProviderException) {
        Log.w(LOG_TAG, "onViewCreated: failed to launch billing flow", e)
        showErrorSnackbar(R.string.failed_to_purchase)
      }
    }
  }
}

@BindingAdapter("donationSkuDetailsList", "onClickSkuDetails")
fun setDonationSkuDetailsList(
  container: ViewGroup,
  skuDetailsList: List<InAppBillingProvider.SkuDetails>,
  clickListener: OnSkuDetailsClickListener
) {
  val sortedSkuDetailsList = skuDetailsList.sortedBy { it.priceAmountMicros }
  if (container.tag == sortedSkuDetailsList) {
    return
  }

  container.tag = sortedSkuDetailsList
  container.removeAllViews()
  sortedSkuDetailsList.forEach { skuDetails ->
    val b = MaterialButton(container.context, null, R.attr.materialButtonOutlinedStyle)
    b.text = skuDetails.price
    b.setOnClickListener { clickListener.onClick(skuDetails) }
    container.addView(b)
  }
}

@HiltViewModel
class InAppDonationViewModel @Inject constructor(
  billingProvider: InAppBillingProvider,
) : ViewModel() {

  var skuDetailsClickListener = OnSkuDetailsClickListener {}
  val skuDetails = MutableStateFlow<List<InAppBillingProvider.SkuDetails>>(emptyList())
  val isLoading = MutableStateFlow(true)
  val error = MutableStateFlow<Throwable?>(null)

  init {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val details = billingProvider.queryDetails(
          InAppBillingProvider.SkuType.INAPP,
          DonationFragmentProvider.IN_APP_DONATION_SKUS,
        )

        skuDetails.emit(details)
      } catch (e: InAppBillingProviderException) {
        Log.w(LOG_TAG, "failed to get sku details", e)
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

fun interface OnSkuDetailsClickListener {
  fun onClick(skuDetails: InAppBillingProvider.SkuDetails)
}

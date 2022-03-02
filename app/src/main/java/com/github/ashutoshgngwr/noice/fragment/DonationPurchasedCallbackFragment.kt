package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.databinding.DonationPurchasedCallbackFragmentBinding
import com.github.ashutoshgngwr.noice.provider.InAppBillingProvider
import com.github.ashutoshgngwr.noice.provider.InAppBillingProviderException
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DonationPurchasedCallbackFragment : BottomSheetDialogFragment() {

  private lateinit var binding: DonationPurchasedCallbackFragmentBinding
  private val viewModel: DonationPurchaseCallbackViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = DonationPurchasedCallbackFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    isCancelable = false
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    viewModel.onDismissClicked = this::dismiss
  }
}

@HiltViewModel
class DonationPurchaseCallbackViewModel @Inject constructor(
  billingProvider: InAppBillingProvider,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  var onDismissClicked: () -> Unit = {}
  val isLoading = MutableStateFlow(true)
  val error = MutableStateFlow<Throwable?>(null)

  init {
    val args = DonationPurchasedCallbackFragmentArgs.fromSavedStateHandle(savedStateHandle)
    viewModelScope.launch(Dispatchers.IO) {
      try {
        billingProvider.consumePurchase(args.purchase)
      } catch (e: InAppBillingProviderException) {
        error.emit(e)
      } finally {
        isLoading.emit(false)
      }
    }
  }
}

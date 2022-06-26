package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.Navigation
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.RedeemGiftCardFragmentBinding
import com.github.ashutoshgngwr.noice.ext.normalizeSpace
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.AlreadySubscribedError
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.trynoice.api.client.models.Subscription
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RedeemGiftCardFragment : BottomSheetDialogFragment() {

  private lateinit var binding: RedeemGiftCardFragmentBinding
  private val viewModel: RedeemGiftCardViewModel by viewModels()
  private val mainNavController by lazy {
    Navigation.findNavController(requireActivity(), R.id.main_nav_host_fragment)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = RedeemGiftCardFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    isCancelable = false
    binding.lifecycleOwner = viewLifecycleOwner
    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.redeemResource
        .filterNot { it is Resource.Loading }
        .collect { dismiss() }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.shouldShowPurchaseList
        .filter { it }
        .collect { mainNavController.navigate(R.id.subscription_purchase_list) }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.errStrRes
        .filterNotNull()
        .map { getString(it) }
        .map { getString(R.string.gift_card_redeem_error, it).normalizeSpace() }
        .collect { showErrorSnackbar(it) }
    }
  }
}

@HiltViewModel
class RedeemGiftCardViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

  internal val redeemResource = MutableStateFlow<Resource<Subscription>>(Resource.Loading())

  internal val shouldShowPurchaseList: StateFlow<Boolean> = redeemResource.transform { r ->
    emit(r is Resource.Success)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  internal val errStrRes: StateFlow<Int?> = redeemResource.transform { r ->
    emit(
      when (r.error) {
        null -> null
        is AlreadySubscribedError -> R.string.user_already_subscribed
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  init {
    val args = RedeemGiftCardFragmentArgs.fromSavedStateHandle(savedStateHandle)
    viewModelScope.launch {
      subscriptionRepository.redeemGiftCard(args.giftCard)
        .flowOn(Dispatchers.IO)
        .collect(redeemResource)
    }
  }
}

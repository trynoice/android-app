package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.Navigation
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.RedeemGiftCardFormFragmentBinding
import com.github.ashutoshgngwr.noice.metrics.AnalyticsProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import javax.inject.Inject

class RedeemGiftCardFormFragment : BottomSheetDialogFragment() {

  @set:Inject
  internal var analyticsProvider: AnalyticsProvider? = null

  private lateinit var binding: RedeemGiftCardFormFragmentBinding
  private val viewModel: RedeemGiftCardFormViewModel by viewModels()
  private val mainNavHost by lazy {
    Navigation.findNavController(requireActivity(), R.id.main_nav_host_fragment)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = RedeemGiftCardFormFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    binding.cancel.setOnClickListener { dismiss() }
    binding.cont.setOnClickListener {
      val args = GiftCardDetailsFragmentArgs(viewModel.code.value)
      mainNavHost.navigate(R.id.gift_card_details, args.toBundle())
    }

    analyticsProvider?.setCurrentScreen(this::class)
  }
}

class RedeemGiftCardFormViewModel : ViewModel() {

  val code = MutableStateFlow("")

  val isCodeValid: StateFlow<Boolean> = code.transform { code ->
    emit(code.isNotBlank() && code.length <= 32)
  }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
}

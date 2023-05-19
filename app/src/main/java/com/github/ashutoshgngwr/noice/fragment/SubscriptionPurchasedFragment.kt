package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SubscriptionPurchasedFragmentBinding
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.SubscriptionNotFoundError
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class SubscriptionPurchasedFragment : BottomSheetDialogFragment() {

  private lateinit var binding: SubscriptionPurchasedFragmentBinding
  private val viewModel: SubscriptionBillingCallbackViewModel by viewModels()
  private val mainNavController: NavController by lazy {
    Navigation.findNavController(requireActivity(), R.id.main_nav_host_fragment)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = SubscriptionPurchasedFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    isCancelable = false
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    binding.okay.setOnClickListener {
      dismiss()
      mainNavController.navigate(R.id.subscription_purchase_list)
    }
  }
}

@HiltViewModel
class SubscriptionBillingCallbackViewModel @Inject constructor(
  private val subscriptionRepository: SubscriptionRepository,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  val isLoading = MutableStateFlow(true)
  val error = MutableStateFlow<Throwable?>(null)

  init {
    val args = SubscriptionPurchasedFragmentArgs.fromSavedStateHandle(savedStateHandle)
    viewModelScope.launch {
      val endTimestamp = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(150)
      subscriptionRepository.get(args.subscriptionId)
        .transform { r ->
          if (r.error != null) {
            throw r.error // throw errors so retry is invoked.
          }

          emit(r)
        }
        .retry { e ->
          // retry for approximately 150 seconds with a 2.5 seconds delay between each attempt.
          delay(2500)
          e is SubscriptionNotFoundError && System.currentTimeMillis() < endTimestamp
        }
        .catch { error.emit(it) }
        .onCompletion { isLoading.emit(false) }
        .collect()
    }
  }
}

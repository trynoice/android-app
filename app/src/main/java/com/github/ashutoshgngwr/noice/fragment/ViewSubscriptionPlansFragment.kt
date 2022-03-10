package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.forEach
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SubscriptionPlanItemBinding
import com.github.ashutoshgngwr.noice.databinding.ViewSubscriptionPlansFragmentBinding
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.provider.NetworkInfoProvider
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.trynoice.api.client.models.SubscriptionPlan
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ViewSubscriptionPlansFragment : Fragment() {

  private lateinit var binding: ViewSubscriptionPlansFragmentBinding
  private val viewModel: ViewSubscriptionPlansViewModel by viewModels()
  private val mainNavController: NavController by lazy {
    Navigation.findNavController(requireActivity(), R.id.main_nav_host_fragment)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = ViewSubscriptionPlansFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    viewModel.onSignInClicked = { mainNavController.navigate(R.id.sign_in_form) }
    viewModel.onSignUpClicked = { mainNavController.navigate(R.id.sign_up_form) }
    viewModel.onPlanSelectedListener = OnPlanSelectedListener { plan ->
      val args = LaunchSubscriptionFlowFragmentArgs(plan)
      mainNavController.navigate(R.id.launch_subscription_flow, args.toBundle())
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.apiErrorStrRes
        .filterNotNull()
        .collect { strRes -> showErrorSnackbar(strRes) }
    }
  }
}

@BindingAdapter("subscriptionPlans", "canClickItems", "onPlanSelected")
fun setSubscriptionPlans(
  container: ViewGroup,
  plans: List<SubscriptionPlan>,
  canClickItems: Boolean,
  onPlanSelectedListener: OnPlanSelectedListener,
) {
  if (plans != container.tag) {
    container.tag = plans
    container.removeAllViews()
    val inflater = LayoutInflater.from(container.context)
    plans.forEach { plan ->
      val binding = SubscriptionPlanItemBinding.inflate(inflater, container, true)
      binding.plan = plan
      binding.root.setOnClickListener { onPlanSelectedListener.onPlanSelected(plan) }
    }
  }

  // setOnClickListener sets isClickable = true internally, and therefore, isClickable should be
  // called after it.
  container.forEach { view ->
    view.isClickable = canClickItems
  }
}

@BindingAdapter("billingPeriodMonths")
fun setBillingPeriodMonths(tv: TextView, months: Int) {
  val resources = tv.context.resources
  tv.text = when (months) {
    1 -> resources.getString(R.string.monthly)
    3 -> resources.getString(R.string.quarterly)
    6 -> resources.getString(R.string.bi_yearly)
    12 -> resources.getString(R.string.yearly)
    else -> resources.getQuantityString(R.plurals.n_months, months, months)
  }
}

@HiltViewModel
class ViewSubscriptionPlansViewModel @Inject constructor(
  accountRepository: AccountRepository,
  subscriptionRepository: SubscriptionRepository,
  networkInfoProvider: NetworkInfoProvider,
) : ViewModel() {

  var onSignInClicked: () -> Unit = {}
  var onSignUpClicked: () -> Unit = {}
  var onPlanSelectedListener = OnPlanSelectedListener {}
  val isSignedIn = accountRepository.isSignedIn()

  private val plansResource = MutableStateFlow<Resource<List<SubscriptionPlan>>>(Resource.Loading())

  val isLoading: StateFlow<Boolean> = plansResource.transform { r ->
    emit(r.data == null)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  val plans: StateFlow<List<SubscriptionPlan>> = plansResource.transform { r ->
    emit(r.data ?: emptyList())
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

  internal val apiErrorStrRes: Flow<Int?> = plansResource.transform { r ->
    emit(
      when {
        r.data != null && networkInfoProvider.isOffline.value -> null
        r.error == null -> null
        r.error is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }

  init {
    viewModelScope.launch {
      networkInfoProvider.isOnline.collect {
        subscriptionRepository.getPlans()
          .flowOn(Dispatchers.IO)
          .collect(plansResource)
      }
    }
  }
}

fun interface OnPlanSelectedListener {
  fun onPlanSelected(plan: SubscriptionPlan)
}

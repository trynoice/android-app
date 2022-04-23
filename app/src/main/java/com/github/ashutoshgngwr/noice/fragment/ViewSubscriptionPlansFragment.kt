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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SubscriptionPlanItemBinding
import com.github.ashutoshgngwr.noice.databinding.ViewSubscriptionPlansFragmentBinding
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.google.android.material.card.MaterialCardView
import com.trynoice.api.client.models.Subscription
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
    binding.onPlanSelectedListener = OnPlanSelectedListener { plan ->
      val args = LaunchSubscriptionFlowFragmentArgs(plan, viewModel.activeSubscription)
      mainNavController.navigate(R.id.launch_subscription_flow, args.toBundle())
    }

    binding.signIn.setOnClickListener { mainNavController.navigate(R.id.sign_in_form) }
    binding.signUp.setOnClickListener { mainNavController.navigate(R.id.sign_up_form) }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.apiErrorStrRes
        .filterNotNull()
        .collect { strRes -> showErrorSnackbar(strRes) }
    }
  }
}

@BindingAdapter("subscriptionPlans", "activePlan", "canClickItems", "onPlanSelected")
fun setSubscriptionPlans(
  container: ViewGroup,
  plans: List<SubscriptionPlan>,
  activePlan: SubscriptionPlan?,
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
      binding.root.tag = plan
    }
  }

  // setOnClickListener sets isClickable = true internally, and therefore, isClickable should be
  // called after it.
  container.forEach { view ->
    view as MaterialCardView
    val isActive = view.tag == activePlan
    view.isCheckable = isActive
    view.isChecked = isActive
    when {
      !canClickItems -> view.isClickable = false
      canClickItems && isActive -> view.setOnClickListener(null)
      else -> view.setOnClickListener {
        onPlanSelectedListener.onPlanSelected(view.tag as SubscriptionPlan)
      }
    }
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
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  val isSignedIn = accountRepository.isSignedIn()
  val activeSubscription: Subscription?

  private val plansResource = MutableStateFlow<Resource<List<SubscriptionPlan>>>(Resource.Loading())

  val isLoading: StateFlow<Boolean> = plansResource.transform { r ->
    emit(r.data == null)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  val plans: StateFlow<List<SubscriptionPlan>> = plansResource.transform { r ->
    emit(r.data ?: emptyList())
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

  internal val apiErrorStrRes: Flow<Int?> = plansResource.transform { r ->
    emit(
      when (r.error) {
        null -> null
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }

  init {
    val args = ViewSubscriptionPlansFragmentArgs.fromSavedStateHandle(savedStateHandle)
    activeSubscription = args.activeSubscription

    viewModelScope.launch {
      subscriptionRepository.getPlans()
        .flowOn(Dispatchers.IO)
        .collect(plansResource)
    }
  }
}

fun interface OnPlanSelectedListener {
  fun onPlanSelected(plan: SubscriptionPlan)
}

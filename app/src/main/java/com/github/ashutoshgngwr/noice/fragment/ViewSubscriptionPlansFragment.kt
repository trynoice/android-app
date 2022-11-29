package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.ConfigurationCompat
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
import com.github.ashutoshgngwr.noice.ext.normalizeSpace
import com.github.ashutoshgngwr.noice.models.Subscription
import com.github.ashutoshgngwr.noice.models.SubscriptionPlan
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SoundRepository
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import java.util.*
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
        .collect { causeStrRes ->
          val msg = getString(R.string.subscription_plans_load_error, getString(causeStrRes))
          binding.errorContainer.message = msg.normalizeSpace()
        }
    }

    val currencyCode = try {
      ConfigurationCompat.getLocales(resources.configuration)
        .get(0)
        .let { Currency.getInstance(it) }
        .currencyCode
    } catch (e: Throwable) {
      // catch "java.lang.IllegalArgumentException: Unsupported ISO 3166 country: ar" or other
      // similar errors and use a default currency.
      "USD"
    }

    binding.errorContainer.retryAction = { viewModel.loadPlans(currencyCode) }
    viewModel.loadPlans(currencyCode)
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
      isActive -> view.setOnClickListener(null)
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
  private val subscriptionRepository: SubscriptionRepository,
  private val soundRepository: SoundRepository,
  accountRepository: AccountRepository,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  val isSignedIn = accountRepository.isSignedIn()
  val activeSubscription: Subscription?

  private val plansResource = MutableSharedFlow<Resource<List<SubscriptionPlan>>>()
  private val premiumCountResource = MutableSharedFlow<Resource<Int>>()

  val isLoading: StateFlow<Boolean> = combine(plansResource, premiumCountResource) { p, c ->
    p is Resource.Loading || c is Resource.Loading
  }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

  val plans: StateFlow<List<SubscriptionPlan>> = plansResource.transform { r ->
    emit(r.data?.sortedBy { it.priceInIndianPaise / it.billingPeriodMonths } ?: emptyList())
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val premiumSoundsCount: StateFlow<Int> = premiumCountResource
    .mapNotNull { r -> r.data }
    .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

  val apiErrorStrRes: StateFlow<Int?> = plansResource.transform { r ->
    emit(
      when (r.error) {
        null -> null
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val isShowingLocalPrices = plans.transform { plans ->
    emit(plans.all { it.requestedCurrencyCode != null && it.requestedCurrencyCode != "INR" })
  }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  init {
    val args = ViewSubscriptionPlansFragmentArgs.fromSavedStateHandle(savedStateHandle)
    activeSubscription = args.activeSubscription
  }

  fun loadPlans(currencyCode: String) {
    viewModelScope.launch {
      subscriptionRepository.listPlans(currencyCode).collect(plansResource)
    }

    viewModelScope.launch {
      soundRepository.countPremium().collect(premiumCountResource)
    }
  }
}

fun interface OnPlanSelectedListener {
  fun onPlanSelected(plan: SubscriptionPlan)
}

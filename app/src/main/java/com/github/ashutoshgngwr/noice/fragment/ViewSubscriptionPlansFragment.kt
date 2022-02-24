package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SubscriptionPlanItemBinding
import com.github.ashutoshgngwr.noice.databinding.ViewSubscriptionPlansFragmentBinding
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.provider.NetworkInfoProvider
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.trynoice.api.client.models.SubscriptionPlan
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class ViewSubscriptionPlansFragment : Fragment() {

  private lateinit var binding: ViewSubscriptionPlansFragmentBinding
  private val viewModel: ViewSubscriptionPlansViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = ViewSubscriptionPlansFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    lifecycleScope.launch {
      viewModel.apiErrorStrRes
        .filterNotNull()
        .collect { strRes -> showErrorSnackbar(strRes) }
    }
  }
}

private val INR_FORMATTER = NumberFormat.getCurrencyInstance().apply {
  currency = Currency.getInstance("INR")
  minimumFractionDigits = 0
}

@BindingAdapter("subscriptionPlans")
fun setSubscriptionPlans(container: ViewGroup, plans: List<SubscriptionPlan>) {
  container.removeAllViews()
  val inflater = LayoutInflater.from(container.context)
  plans.forEach {
    val binding = SubscriptionPlanItemBinding.inflate(inflater, container, true)
    binding.plan = it
    binding.formatIndianPaise = { p -> INR_FORMATTER.format(p / 100.0) }
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
  subscriptionRepository: SubscriptionRepository,
  networkInfoProvider: NetworkInfoProvider,
) : ViewModel() {

  private val plansResource = MutableStateFlow<Resource<List<SubscriptionPlan>>>(Resource.Loading())

  val isLoading: StateFlow<Boolean> = plansResource.transform { r ->
    emit(r.data == null)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  val plans: StateFlow<List<SubscriptionPlan>> = plansResource.transform { r ->
    emit(r.data ?: emptyList())
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

  internal val apiErrorStrRes: StateFlow<Int?> = plansResource.transform { r ->
    emit(
      when {
        r.data != null && networkInfoProvider.isOffline.value -> null
        r.error == null -> null
        r.error is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

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

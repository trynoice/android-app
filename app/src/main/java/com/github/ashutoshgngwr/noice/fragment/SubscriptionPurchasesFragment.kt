package com.github.ashutoshgngwr.noice.fragment

import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.ConfigurationCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.paging.PagingData
import androidx.paging.PagingDataAdapter
import androidx.paging.cachedIn
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SubscriptionPurchaseItemBinding
import com.github.ashutoshgngwr.noice.databinding.SubscriptionPurchaseLoadingItemBinding
import com.github.ashutoshgngwr.noice.databinding.SubscriptionPurchasesFragmentBinding
import com.github.ashutoshgngwr.noice.ext.launchAndRepeatOnStarted
import com.github.ashutoshgngwr.noice.ext.normalizeSpace
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.models.Subscription
import com.github.ashutoshgngwr.noice.models.SubscriptionPlan
import com.github.ashutoshgngwr.noice.provider.AnalyticsProvider
import com.github.ashutoshgngwr.noice.provider.SubscriptionBillingProvider
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Currency
import javax.inject.Inject

@AndroidEntryPoint
class SubscriptionPurchasesFragment : Fragment(), SubscriptionPurchaseViewHolder.ViewController {

  @set:Inject
  internal lateinit var analyticsProvider: AnalyticsProvider

  private lateinit var binding: SubscriptionPurchasesFragmentBinding
  private val viewModel: SubscriptionPurchasesViewModel by viewModels()
  private val mainNavController: NavController by lazy {
    Navigation.findNavController(requireActivity(), R.id.main_nav_host_fragment)
  }

  @set:Inject
  internal lateinit var subscriptionBillingProvider: SubscriptionBillingProvider

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = SubscriptionPurchasesFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val adapter = SubscriptionPurchaseListAdapter(layoutInflater, this, subscriptionBillingProvider)
    val footerAdapter = SubscriptionPurchaseListLoadStateAdapter(layoutInflater, adapter::retry)
    binding.list.adapter = ConcatAdapter(adapter, footerAdapter)
    binding.errorContainer.retryAction = adapter::refresh
    adapter.addLoadStateListener { loadStates ->
      footerAdapter.loadState = loadStates.append
      val isRefreshError = loadStates.refresh is LoadState.Error
      val isListEmpty = loadStates.refresh is LoadState.NotLoading
        && loadStates.append.endOfPaginationReached
        && adapter.itemCount < 1

      binding.emptyListIndicator.isVisible = isListEmpty
      binding.errorContainer.isVisible = isRefreshError && adapter.itemCount < 1
      binding.swipeContainer.isVisible =
        !(binding.emptyListIndicator.isVisible || binding.errorContainer.isVisible)
      binding.swipeContainer.isRefreshing = loadStates.refresh is LoadState.Loading

      if (isRefreshError) {
        val message = buildLoadError(requireContext(), loadStates.refresh)
        if (binding.errorContainer.isVisible) {
          binding.errorContainer.message = message
        } else {
          showErrorSnackBar(message)
            .setAction(R.string.retry) { adapter.refresh() }
        }
      }
    }

    binding.swipeContainer.setOnRefreshListener(adapter::refresh)
    binding.viewSubscriptionPlans.setOnClickListener {
      mainNavController.navigate(R.id.view_subscription_plans)
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.purchasesData.collectLatest(adapter::submitData)
    }

    setFragmentResultListener(CancelSubscriptionFragment.RESULT_KEY) { _, bundle ->
      if (!bundle.getBoolean(CancelSubscriptionFragment.EXTRA_WAS_ABORTED, true)) {
        adapter.refresh()
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

    viewModel.createPager(currencyCode)
    analyticsProvider.setCurrentScreen(this::class)
  }

  override fun onSubscriptionManageClicked(subscription: Subscription) {
    mainNavController.navigate(R.id.launch_stripe_customer_portal)
  }

  override fun onSubscriptionUpgradeClicked(subscription: Subscription) {
    val args = ViewSubscriptionPlansFragmentArgs(subscription).toBundle()
    mainNavController.navigate(R.id.view_subscription_plans, args)
  }

  override fun onSubscriptionCancelClicked(subscription: Subscription) {
    val args = CancelSubscriptionFragmentArgs(subscription).toBundle()
    mainNavController.navigate(R.id.cancel_subscription, args)
  }

  companion object {
    internal const val URI = "noice://subscriptions/purchases"
  }
}

@HiltViewModel
class SubscriptionPurchasesViewModel @Inject constructor(
  private val subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

  internal val purchasesData = MutableStateFlow<PagingData<Subscription>>(PagingData.empty())
  private var currencyCode: String? = null
  private var pagerJob: Job? = null

  internal fun createPager(currencyCode: String) {
    if (this.currencyCode == currencyCode) {
      return
    }

    this.currencyCode = currencyCode
    pagerJob?.cancel()
    pagerJob = viewModelScope.launch {
      subscriptionRepository.pagingDataFlow(currencyCode)
        .cachedIn(this)
        .collect(purchasesData)
    }
  }
}

class SubscriptionPurchaseListAdapter(
  private val layoutInflater: LayoutInflater,
  private val viewController: SubscriptionPurchaseViewHolder.ViewController,
  private val subscriptionBillingProvider: SubscriptionBillingProvider,
) : PagingDataAdapter<Subscription, SubscriptionPurchaseViewHolder>(diffCallback) {

  override fun onBindViewHolder(holder: SubscriptionPurchaseViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  override fun onCreateViewHolder(parent: ViewGroup, type: Int): SubscriptionPurchaseViewHolder {
    val binding = SubscriptionPurchaseItemBinding.inflate(layoutInflater, parent, false)
    return SubscriptionPurchaseViewHolder(binding, viewController, subscriptionBillingProvider)
  }

  companion object {
    private val diffCallback = object : DiffUtil.ItemCallback<Subscription>() {
      override fun areItemsTheSame(oldItem: Subscription, newItem: Subscription): Boolean {
        return oldItem.id == newItem.id
      }

      override fun areContentsTheSame(oldItem: Subscription, newItem: Subscription): Boolean {
        return oldItem == newItem
      }
    }
  }
}

class SubscriptionPurchaseViewHolder(
  private val binding: SubscriptionPurchaseItemBinding,
  private val viewController: ViewController,
  private val subscriptionBillingProvider: SubscriptionBillingProvider,
) : RecyclerView.ViewHolder(binding.root) {

  fun bind(s: Subscription?) {
    s ?: return

    val context = binding.root.context
    val resources = binding.root.resources
    val isGiftCardPurchase = s.plan.provider == SubscriptionPlan.PROVIDER_GIFT_CARD
    binding.billingPeriod.text = if (isGiftCardPurchase) {
      resources.getString(R.string.gift_card)
    } else {
      when (s.plan.billingPeriodMonths) {
        1 -> resources.getString(R.string.monthly)
        3 -> resources.getString(R.string.quarterly)
        6 -> resources.getString(R.string.bi_yearly)
        12 -> resources.getString(R.string.yearly)
        else -> resources.getQuantityString(
          R.plurals.n_months,
          s.plan.billingPeriodMonths,
          s.plan.billingPeriodMonths,
        )
      }
    }

    binding.monthlyPrice.isVisible = !isGiftCardPurchase
    if (!isGiftCardPurchase) {
      binding.monthlyPrice.text = s.plan.monthlyPrice(true)
        .also { resources.getString(R.string.monthly_price, it) }
    }

    binding.startedOn.text = s.startedAt?.let {
      resources.getString(
        R.string.started_on,
        DateUtils.formatDateTime(context, it.time, DATE_FMT_FLAGS)
      )
    }

    when {
      !s.isAutoRenewing && s.renewsAt != null -> {
        binding.endedOn.isVisible = true
        binding.endedOn.text = resources.getString(
          R.string.ends_on,
          DateUtils.formatDateTime(context, s.renewsAt.time, DATE_FMT_FLAGS)
        )
      }

      s.endedAt != null -> {
        binding.endedOn.isVisible = true
        binding.endedOn.text = resources.getString(
          R.string.ended_on,
          DateUtils.formatDateTime(context, s.endedAt.time, DATE_FMT_FLAGS)
        )
      }

      else -> binding.endedOn.isVisible = false
    }

    binding.renewsOn.isVisible = s.isAutoRenewing && s.renewsAt != null
    binding.renewsOn.text = s.renewsAt?.let {
      resources.getString(
        R.string.renews_on,
        DateUtils.formatDateTime(context, it.time, DATE_FMT_FLAGS)
      )
    }

    binding.paidUsing.isVisible = !isGiftCardPurchase
    binding.redeemedUsing.isVisible = isGiftCardPurchase

    if (isGiftCardPurchase) {
      binding.redeemedUsing.text = resources.getString(R.string.redeemed_using_code, s.giftCardCode)
    } else {
      binding.paidUsing.text = resources.getString(
        R.string.paid_using, resources.getString(
          when (s.plan.provider) {
            SubscriptionPlan.PROVIDER_STRIPE -> R.string.stripe
            SubscriptionPlan.PROVIDER_GOOGLE_PLAY -> R.string.google_play
            else -> throw IllegalArgumentException("unknown payment provider")
          }
        )
      )
    }

    binding.refunded.isVisible = s.isRefunded == true
    binding.paymentPending.isVisible = s.isPaymentPending
    binding.actionButtonContainer.isVisible = s.isActive
    if (s.isActive) {
      binding.manage.isVisible = s.plan.provider == SubscriptionPlan.PROVIDER_STRIPE
      binding.manage.setOnClickListener { viewController.onSubscriptionManageClicked(s) }
      binding.changePlan.isVisible = subscriptionBillingProvider.canUpgrade(s)
      binding.changePlan.setOnClickListener { viewController.onSubscriptionUpgradeClicked(s) }
      binding.cancel.isVisible = s.isAutoRenewing
      binding.cancel.setOnClickListener { viewController.onSubscriptionCancelClicked(s) }
    }
  }

  companion object {
    private const val DATE_FMT_FLAGS = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME
  }


  interface ViewController {
    fun onSubscriptionManageClicked(subscription: Subscription)
    fun onSubscriptionUpgradeClicked(subscription: Subscription)
    fun onSubscriptionCancelClicked(subscription: Subscription)
  }
}

class SubscriptionPurchaseListLoadStateAdapter(
  private val layoutInflater: LayoutInflater,
  private val retryAction: () -> Unit,
) : LoadStateAdapter<SubscriptionPurchaseLoadingViewHolder>() {

  override fun onBindViewHolder(
    holder: SubscriptionPurchaseLoadingViewHolder,
    loadState: LoadState,
  ) {
    holder.bind(loadState)
  }

  override fun onCreateViewHolder(
    parent: ViewGroup,
    loadState: LoadState,
  ): SubscriptionPurchaseLoadingViewHolder {
    val binding = SubscriptionPurchaseLoadingItemBinding.inflate(layoutInflater, parent, false)
    return SubscriptionPurchaseLoadingViewHolder(binding, retryAction)
  }
}

class SubscriptionPurchaseLoadingViewHolder(
  private val binding: SubscriptionPurchaseLoadingItemBinding,
  private val retryAction: () -> Unit,
) : RecyclerView.ViewHolder(binding.root) {

  fun bind(loadState: LoadState) {
    binding.progressIndicator.isVisible = loadState is LoadState.Loading
    binding.errorContainer.isVisible = loadState is LoadState.Error
    if (loadState is LoadState.Error) {
      binding.retryButton.setOnClickListener { retryAction.invoke() }
      binding.errorMessage.text = buildLoadError(binding.root.context, loadState)
    }
  }
}

private fun buildLoadError(context: Context, loadState: LoadState): String {
  require(loadState is LoadState.Error) { "given loadState is not a LoadState.Error instance" }
  val cause = context.getString(
    when (loadState.error) {
      is NetworkError -> R.string.network_error
      else -> R.string.unknown_error
    }
  )

  return context.getString(R.string.subscription_purchases_load_error, cause).normalizeSpace()
}

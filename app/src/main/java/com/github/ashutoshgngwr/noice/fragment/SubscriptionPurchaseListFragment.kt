package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingDataAdapter
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SubscriptionPurchaseItemBinding
import com.github.ashutoshgngwr.noice.databinding.SubscriptionPurchaseListFragmentBinding
import com.github.ashutoshgngwr.noice.databinding.SubscriptionPurchaseLoadingItemBinding
import com.github.ashutoshgngwr.noice.ext.startCustomTab
import com.github.ashutoshgngwr.noice.provider.NetworkInfoProvider
import com.github.ashutoshgngwr.noice.provider.SubscriptionBillingProvider
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.trynoice.api.client.models.Subscription
import com.trynoice.api.client.models.SubscriptionPlan
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SubscriptionPurchaseListFragment : Fragment(), SubscriptionActionClickListener {

  private lateinit var binding: SubscriptionPurchaseListFragmentBinding
  private val viewModel: SubscriptionPurchaseListViewModel by viewModels()
  private val mainNavController: NavController by lazy {
    Navigation.findNavController(requireActivity(), R.id.main_nav_host_fragment)
  }

  @set:Inject
  internal lateinit var subscriptionRepository: SubscriptionRepository

  @set:Inject
  internal lateinit var subscriptionBillingProvider: SubscriptionBillingProvider

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = SubscriptionPurchaseListFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val adapter = SubscriptionPurchaseListAdapter(layoutInflater, this, subscriptionBillingProvider)
    val headerAdapter = SubscriptionPurchaseListLoadStateAdapter(layoutInflater, adapter::retry)
    val footerAdapter = SubscriptionPurchaseListLoadStateAdapter(layoutInflater, adapter::retry)
    binding.list.adapter = ConcatAdapter(headerAdapter, adapter, footerAdapter)
    adapter.addLoadStateListener { loadStates ->
      headerAdapter.loadState = loadStates.refresh
      footerAdapter.loadState = loadStates.append

      val isListEmpty = loadStates.source.refresh is LoadState.NotLoading
        && loadStates.append.endOfPaginationReached
        && adapter.itemCount < 1

      binding.list.isVisible = !isListEmpty
      binding.emptyListIndicator.isVisible = isListEmpty
    }

    binding.viewSubscriptionPlans.setOnClickListener {
      mainNavController.navigate(R.id.view_subscription_plans)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.purchasesPager.collectLatest(adapter::submitData)
    }

    setFragmentResultListener(CancelSubscriptionFragment.RESULT_KEY) { _, bundle ->
      if (!bundle.getBoolean(CancelSubscriptionFragment.EXTRA_WAS_ABORTED, true)) {
        adapter.refresh()
      }
    }
  }

  override fun onClickManage(subscription: Subscription) {
    subscription.stripeCustomerPortalUrl?.also { activity?.startCustomTab(it) }
  }

  override fun onClickUpgrade(subscription: Subscription) {
    val args = ViewSubscriptionPlansFragmentArgs(subscription).toBundle()
    mainNavController.navigate(R.id.view_subscription_plans, args)
  }

  override fun onClickCancel(subscription: Subscription) {
    val args = CancelSubscriptionFragmentArgs(subscription).toBundle()
    mainNavController.navigate(R.id.cancel_subscription, args)
  }

  companion object {
    internal const val URI = "noice://subscriptions/purchases"
  }
}

@HiltViewModel
class SubscriptionPurchaseListViewModel @Inject constructor(
  subscriptionRepository: SubscriptionRepository,
  networkInfoProvider: NetworkInfoProvider,
) : ViewModel() {

  internal val purchasesPager = Pager(PagingConfig(pageSize = 20)) {
    SubscriptionPurchasePagingDataSource(subscriptionRepository, networkInfoProvider)
  }.flow.cachedIn(viewModelScope)
}

class SubscriptionPurchaseListAdapter(
  private val layoutInflater: LayoutInflater,
  private val actionClickListener: SubscriptionActionClickListener,
  private val subscriptionBillingProvider: SubscriptionBillingProvider,
) : PagingDataAdapter<Subscription, SubscriptionPurchaseViewHolder>(SubscriptionComparator) {

  override fun onBindViewHolder(holder: SubscriptionPurchaseViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  override fun onCreateViewHolder(parent: ViewGroup, type: Int): SubscriptionPurchaseViewHolder {
    val binding = SubscriptionPurchaseItemBinding.inflate(layoutInflater, parent, false)
    return SubscriptionPurchaseViewHolder(binding, actionClickListener, subscriptionBillingProvider)
  }
}

class SubscriptionPurchaseViewHolder(
  private val binding: SubscriptionPurchaseItemBinding,
  private val actionClickListener: SubscriptionActionClickListener,
  private val subscriptionBillingProvider: SubscriptionBillingProvider,
) : RecyclerView.ViewHolder(binding.root) {

  fun bind(s: Subscription?) {
    s ?: return

    val context = binding.root.context
    val resources = binding.root.resources
    binding.billingPeriod.text = when (s.plan.billingPeriodMonths) {
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

    binding.monthlyPrice.text = resources.getString(R.string.monthly_price, s.plan.monthlyPrice)
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
          s.renewsAt?.let { DateUtils.formatDateTime(context, it.time, DATE_FMT_FLAGS) }
        )
      }

      s.endedAt != null -> {
        binding.endedOn.isVisible = true
        binding.endedOn.text = resources.getString(
          R.string.ended_on,
          s.endedAt?.let { DateUtils.formatDateTime(context, it.time, DATE_FMT_FLAGS) }
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

    binding.paidUsing.text = resources.getString(
      R.string.paid_using, resources.getString(
        when (s.plan.provider) {
          SubscriptionPlan.PROVIDER_STRIPE -> R.string.stripe
          SubscriptionPlan.PROVIDER_GOOGLE_PLAY -> R.string.google_play
          else -> throw IllegalArgumentException("unknown payment provider")
        }
      )
    )

    binding.paymentPending.isVisible = s.isPaymentPending
    binding.actionButtonContainer.isVisible = s.isActive
    if (s.isActive) {
      binding.manage.isVisible = s.stripeCustomerPortalUrl != null
      binding.manage.setOnClickListener { actionClickListener.onClickManage(s) }
      binding.changePlan.isVisible = subscriptionBillingProvider.canUpgrade(s)
      binding.changePlan.setOnClickListener { actionClickListener.onClickUpgrade(s) }
      binding.cancel.isVisible = s.isAutoRenewing
      binding.cancel.setOnClickListener { actionClickListener.onClickCancel(s) }
    }
  }

  companion object {
    private const val DATE_FMT_FLAGS = DateUtils.FORMAT_SHOW_DATE
  }
}

object SubscriptionComparator : DiffUtil.ItemCallback<Subscription>() {

  override fun areItemsTheSame(oldItem: Subscription, newItem: Subscription): Boolean {
    return oldItem.id == newItem.id
  }

  override fun areContentsTheSame(oldItem: Subscription, newItem: Subscription): Boolean {
    return oldItem == newItem
  }
}

class SubscriptionPurchasePagingDataSource(
  private val subscriptionRepository: SubscriptionRepository,
  private val networkInfoProvider: NetworkInfoProvider,
) : PagingSource<Int, Subscription>() {

  override fun getRefreshKey(state: PagingState<Int, Subscription>): Int? {
    return state.anchorPosition?.let { anchorPosition ->
      val anchorPage = state.closestPageToPosition(anchorPosition)
      anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
    }
  }

  override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Subscription> {
    val page = params.key ?: 0
    val resource = subscriptionRepository.list(page)
      .flowOn(Dispatchers.IO)
      .last()

    val nextPage = if (resource.data?.isNotEmpty() == true) {
      page + 1
    } else {
      null
    }

    if (resource.data != null && networkInfoProvider.isOffline.value) { // offline mode
      return LoadResult.Page(resource.data, prevKey = null, nextKey = nextPage)
    }

    if (resource.error != null || resource.data == null) {
      return LoadResult.Error(resource.error ?: throw NullPointerException("resource data is null"))
    }

    return LoadResult.Page(resource.data, prevKey = null, nextKey = nextPage)
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
      binding.errorMessage.setText(
        when (loadState.error) {
          is NetworkError -> R.string.network_error
          else -> R.string.unknown_error
        }
      )
    }
  }
}

interface SubscriptionActionClickListener {
  fun onClickManage(subscription: Subscription)
  fun onClickUpgrade(subscription: Subscription)
  fun onClickCancel(subscription: Subscription)
}

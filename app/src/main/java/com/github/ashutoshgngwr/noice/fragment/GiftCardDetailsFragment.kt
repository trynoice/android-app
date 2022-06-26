package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.GiftCardDetailsFragmentBinding
import com.github.ashutoshgngwr.noice.ext.normalizeSpace
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.SubscriptionRepository
import com.github.ashutoshgngwr.noice.repository.errors.GiftCardNotFoundError
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.trynoice.api.client.models.GiftCard
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class GiftCardDetailsFragment : BottomSheetDialogFragment() {

  private lateinit var binding: GiftCardDetailsFragmentBinding
  private val viewModel: GiftCardDetailsViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = GiftCardDetailsFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    binding.cancel.setOnClickListener { dismiss() }
    binding.redeem.setOnClickListener {
      // TODO
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.giftCard
        .filterNotNull()
        .map { it.hourCredits }
        .collect { hourCredits ->
          val until = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(hourCredits.toLong())
          binding.details.text = (DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)
            .let { DateUtils.formatDateTime(requireContext(), until, it) }
            .let { getString(R.string.gift_card_details, it) }

        }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewModel.errStrRes
        .filterNotNull()
        .map { getString(it) }
        .collect { cause ->
          showErrorSnackbar(getString(R.string.gift_card_load_error, cause).normalizeSpace())
          dismiss()
        }
    }
  }
}

@HiltViewModel
class GiftCardDetailsViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  subscriptionRepository: SubscriptionRepository,
) : ViewModel() {

  private val giftCardResource = MutableSharedFlow<Resource<GiftCard>>()

  val isLoading: StateFlow<Boolean> = giftCardResource.transform { r ->
    emit(r is Resource.Loading)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), true)

  val giftCard: StateFlow<GiftCard?> = giftCardResource.transform { r ->
    emit(r.data)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  internal val errStrRes: StateFlow<Int?> = giftCardResource.transform { r ->
    emit(
      when (r.error) {
        null -> null
        is GiftCardNotFoundError -> R.string.gift_card_not_found
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  init {
    val args = GiftCardDetailsFragmentArgs.fromSavedStateHandle(savedStateHandle)
    viewModelScope.launch {
      subscriptionRepository.getGiftCard(args.giftCardCode)
        .flowOn(Dispatchers.IO)
        .collect(giftCardResource)
    }
  }
}

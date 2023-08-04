package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SignOutFragmentBinding
import com.github.ashutoshgngwr.noice.ext.launchAndRepeatOnStarted
import com.github.ashutoshgngwr.noice.ext.normalizeSpace
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.metrics.AnalyticsProvider
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SignOutFragment : BottomSheetDialogFragment() {

  @set:Inject
  internal var analyticsProvider: AnalyticsProvider? = null

  private lateinit var binding: SignOutFragmentBinding
  private val viewModel: SignOutViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = SignOutFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    binding.cancel.setOnClickListener { dismiss() }
    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.isSigningOut.collect { isCancelable = !it }
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.signOutErrorStrRes
        .filterNotNull()
        .collect { causeStrRes ->
          val msg = getString(R.string.sign_out_error, getString(causeStrRes))
          showErrorSnackBar(msg.normalizeSpace())
        }
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.signOutResource
        .filterNot { it is Resource.Loading }
        .collect { dismiss() }
    }

    analyticsProvider?.setCurrentScreen(this::class)
  }
}

@HiltViewModel
class SignOutViewModel @Inject constructor(
  private val accountRepository: AccountRepository
) : ViewModel() {

  internal val signOutResource = MutableSharedFlow<Resource<Unit>>()

  val isSigningOut: StateFlow<Boolean> = signOutResource.transform { r ->
    emit(r is Resource.Loading)
  }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  val signOutErrorStrRes: Flow<Int?> = signOutResource.transform { r ->
    emit(
      when (r.error) {
        null -> null
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }

  fun signOut() = viewModelScope.launch {
    accountRepository.signOut().collect(signOutResource)
  }
}

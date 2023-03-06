package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.EditAccountDetailsFragmentBinding
import com.github.ashutoshgngwr.noice.ext.launchAndRepeatOnStarted
import com.github.ashutoshgngwr.noice.ext.normalizeSpace
import com.github.ashutoshgngwr.noice.ext.showErrorSnackBar
import com.github.ashutoshgngwr.noice.ext.showSuccessSnackBar
import com.github.ashutoshgngwr.noice.models.Profile
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.errors.DuplicateEmailError
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class EditAccountDetailsFragment : Fragment() {

  private lateinit var binding: EditAccountDetailsFragmentBinding
  private val viewModel: EditAccountDetailsViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = EditAccountDetailsFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.loadErrorStrRes
        .filterNotNull()
        .collect { causeStrRes ->
          val msg = getString(R.string.profile_load_error, getString(causeStrRes))
          binding.errorContainer.message = msg.normalizeSpace()
        }
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.updateResource
        .filter { it is Resource.Success }
        .collect { showSuccessSnackBar(R.string.account_details_update_success, binding.save) }
    }

    viewLifecycleOwner.launchAndRepeatOnStarted {
      viewModel.updateErrorStrRes
        .filterNotNull()
        .collect { causeStrRes ->
          val msg = getString(R.string.account_details_update_error, getString(causeStrRes))
          showErrorSnackBar(msg.normalizeSpace(), binding.save)
        }
    }

    viewModel.loadProfile()
  }
}

@HiltViewModel
class EditAccountDetailsViewModel @Inject constructor(
  private val accountRepository: AccountRepository,
) : ViewModel() {

  val name = MutableStateFlow("")
  val email = MutableStateFlow("")

  private val loadResource = MutableSharedFlow<Resource<Profile>>()
  internal val updateResource = MutableSharedFlow<Resource<Unit>>()

  val isNameValid: StateFlow<Boolean> = name.transform { name ->
    emit(name.isNotBlank() && name.length <= 64)
  }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  val isEmailValid: StateFlow<Boolean> = email.transform { email ->
    emit(
      email.isNotBlank()
        && email.length <= 64
        && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    )
  }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  val isLoading: StateFlow<Boolean> = merge(loadResource, updateResource.filterNotNull())
    .transform { emit(it is Resource.Loading) }
    .stateIn(viewModelScope, SharingStarted.Eagerly, true)

  val loadErrorStrRes: StateFlow<Int?> = loadResource.transform { r ->
    emit(r.error?.let {
      when (it) {
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    })
  }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val updateErrorStrRes: Flow<Int?> = updateResource.transform { r ->
    emit(r.error?.let {
      when (r.error) {
        is DuplicateEmailError -> R.string.duplicate_email_error
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    })
  }

  fun loadProfile() {
    viewModelScope.launch {
      accountRepository.getProfile()
        .onEach { resource ->
          if (resource.data != null) {
            name.emit(resource.data.name)
            email.emit(resource.data.email)
          }
        }
        .collect(loadResource)
    }
  }

  fun saveProfile() {
    if (!isNameValid.value || !isEmailValid.value) {
      return
    }

    viewModelScope.launch {
      accountRepository.updateProfile(email.value, name.value).collect(updateResource)
    }
  }
}

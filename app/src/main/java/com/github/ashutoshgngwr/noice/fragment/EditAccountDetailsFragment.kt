package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.EditAccountDetailsFragmentBinding
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.ext.showSuccessSnackbar
import com.github.ashutoshgngwr.noice.model.DuplicateEmailError
import com.github.ashutoshgngwr.noice.model.NetworkError
import com.github.ashutoshgngwr.noice.model.Resource
import com.github.ashutoshgngwr.noice.provider.NetworkInfoProvider
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.trynoice.api.client.models.Profile
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import org.apache.commons.validator.routines.EmailValidator
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
    viewModel.onUpdateSuccess = { showSuccessSnackbar(R.string.profile_update_success) }
    lifecycleScope.launch {
      viewModel.errorStrRes
        .filterNotNull()
        .collect { strRes -> showErrorSnackbar(strRes) }
    }
  }
}

@HiltViewModel
class EditAccountDetailsViewModel @Inject constructor(
  private val accountRepository: AccountRepository,
  private val networkInfoProvider: NetworkInfoProvider,
) : ViewModel() {

  var onUpdateSuccess: () -> Unit = {}
  val name = MutableStateFlow("")
  val email = MutableStateFlow("")

  private val loadResource = MutableStateFlow<Resource<Profile>>(Resource.Loading())
  private val updateResource = MutableStateFlow<Resource<Unit>?>(null)

  val isNameValid: StateFlow<Boolean> = name.transform { name ->
    emit(name.isNotBlank() && name.length <= 64)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  val isEmailValid: StateFlow<Boolean> = email.transform { email ->
    emit(
      email.isNotBlank()
        && email.length <= 64
        && EmailValidator.getInstance(false, false).isValid(email)
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  val isLoading: StateFlow<Boolean> = merge(loadResource, updateResource.filterNotNull())
    .transform { emit(it is Resource.Loading) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  internal val errorStrRes: StateFlow<Int?> = merge(loadResource, updateResource).transform { r ->
    emit(
      when (r?.error) {
        null -> null
        is DuplicateEmailError -> R.string.duplicate_email_error
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  init {
    viewModelScope.launch {
      networkInfoProvider.isOnline.collect {
        accountRepository.getProfile()
          .flowOn(Dispatchers.IO)
          .onEach { resource ->
            if (resource.data != null) {
              name.emit(resource.data.name)
              email.emit(resource.data.email)
            }
          }
          .collect(loadResource)
      }
    }
  }

  fun save() {
    if (!isNameValid.value || !isEmailValid.value) {
      return
    }

    viewModelScope.launch {
      accountRepository
        .updateProfile(email.value, name.value)
        .flowOn(Dispatchers.IO)
        .onEach { resource ->
          if (resource is Resource.Success) {
            onUpdateSuccess.invoke()
          }
        }
        .collect(updateResource)
    }
  }
}

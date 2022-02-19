package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.Navigation
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.EditAccountDetailsFragmentBinding
import com.github.ashutoshgngwr.noice.ext.showErrorSnackbar
import com.github.ashutoshgngwr.noice.ext.showSuccessSnackbar
import com.github.ashutoshgngwr.noice.model.DuplicateEmailError
import com.github.ashutoshgngwr.noice.model.NetworkError
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.validator.routines.EmailValidator
import javax.inject.Inject

@AndroidEntryPoint
class EditAccountDetailsFragment : Fragment() {

  private lateinit var binding: EditAccountDetailsFragmentBinding
  private val viewModel: EditAccountDetailsViewModel by viewModels()
  private val mainNavController by lazy {
    Navigation.findNavController(requireActivity(), R.id.main_nav_host_fragment)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = EditAccountDetailsFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    viewModel.onProfileUpdated = {
      showSuccessSnackbar(R.string.profile_update_success)
      mainNavController.navigateUp()
    }

    lifecycleScope.launch {
      viewModel.apiErrorStrRes
        .filterNotNull()
        .filterNot { strRes -> strRes == ResourcesCompat.ID_NULL }
        .collect { strRes -> showErrorSnackbar(strRes) }
    }
  }
}

@HiltViewModel
class EditAccountDetailsViewModel @Inject constructor(
  private val accountRepository: AccountRepository,
) : ViewModel() {

  var onProfileUpdated: () -> Unit = {}
  val isLoading = MutableStateFlow(false)
  val name = MutableStateFlow("")
  val email = MutableStateFlow("")
  val isEditingEnabled = MutableStateFlow(false)
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

  private val apiError = MutableStateFlow<Throwable?>(null)
  internal val apiErrorStrRes: StateFlow<Int?> = apiError.transform { e ->
    emit(
      when (e) {
        null -> null
        is DuplicateEmailError -> R.string.duplicate_email_error
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  init {
    viewModelScope.launch(Dispatchers.IO) {
      isLoading.emit(true)
      try {
        // last implies network first value.
        val profile = accountRepository.getProfile().last()
        name.emit(profile.name)
        email.emit(profile.email)
        isEditingEnabled.emit(true)
      } catch (e: Throwable) {
        apiError.emit(e)
      } finally {
        isLoading.emit(false)
      }
    }
  }

  fun save() {
    if (!isNameValid.value || !isEmailValid.value) {
      return
    }

    viewModelScope.launch(Dispatchers.IO) {
      apiError.emit(null)
      isLoading.emit(true)
      try {
        accountRepository.updateProfile(email.value, name.value)
        withContext(Dispatchers.Main) { onProfileUpdated.invoke() }
      } catch (e: Throwable) {
        apiError.emit(e)
      } finally {
        isLoading.emit(false)
      }
    }
  }
}

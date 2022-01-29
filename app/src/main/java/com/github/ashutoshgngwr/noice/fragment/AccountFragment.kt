package com.github.ashutoshgngwr.noice.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.github.ashutoshgngwr.noice.databinding.AccountFragmentBinding
import com.trynoice.api.client.NoiceApiClient
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@AndroidEntryPoint
class AccountFragment : Fragment() {

  private lateinit var binding: AccountFragmentBinding
  private val viewModel: AccountViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = AccountFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
  }
}

@HiltViewModel
class AccountViewModel @Inject constructor(private val apiClient: NoiceApiClient) : ViewModel() {

  val isUserSignedIn = MutableLiveData(apiClient.isSignedIn())
  val isLoadingProfile = MutableLiveData(false)

  init {
    apiClient.addSignInStateListener(isUserSignedIn::postValue)
  }

  override fun onCleared() {
    apiClient.removeSignInStateListener(isUserSignedIn::postValue)
  }

  fun loadUserProfile() {

  }
}

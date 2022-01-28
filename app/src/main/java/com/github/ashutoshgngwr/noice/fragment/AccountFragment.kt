package com.github.ashutoshgngwr.noice.fragment

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.github.ashutoshgngwr.noice.NoiceApplication
import com.github.ashutoshgngwr.noice.databinding.AccountFragmentBinding

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

class AccountViewModel(application: Application) : AndroidViewModel(application) {

  private val app: NoiceApplication = getApplication()

  val isUserSignedIn = MutableLiveData(app.apiClient.isSignedIn())
  val isLoadingProfile = MutableLiveData(false)

  init {
    app.apiClient.addSignInStateListener(isUserSignedIn::postValue)
  }

  override fun onCleared() {
    app.apiClient.removeSignInStateListener(isUserSignedIn::postValue)
  }

  fun loadUserProfile() {

  }
}

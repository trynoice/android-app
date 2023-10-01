package com.github.ashutoshgngwr.noice.activity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SignInLinkHandlerFragmentBinding
import com.github.ashutoshgngwr.noice.metrics.AnalyticsProvider
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.NotSignedInError
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SignInLinkHandlerActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val isSupportedUri = intent.dataString?.startsWith("https://trynoice.com/sign-in") == true
      || intent.dataString?.startsWith("noice://sign-in") == true

    val signInToken = intent.data?.getQueryParameter("token")
    if (Intent.ACTION_VIEW != intent.action || !isSupportedUri || signInToken == null) {
      finish()
      return
    }

    SignInLinkHandlerFragment.newInstance(signInToken)
      .show(supportFragmentManager, "SignInLinkHandlerFragment")
  }
}

@AndroidEntryPoint
class SignInLinkHandlerFragment : BottomSheetDialogFragment() {

  @set:Inject
  internal var analyticsProvider: AnalyticsProvider? = null

  private lateinit var binding: SignInLinkHandlerFragmentBinding
  private val viewModel: SignInLinkHandlerViewModel by viewModels()

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
    binding = SignInLinkHandlerFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    isCancelable = false
    binding.lifecycleOwner = viewLifecycleOwner
    binding.viewModel = viewModel
    binding.dismiss.setOnClickListener { requireActivity().finish() }
    binding.continuu.setOnClickListener {
      requireActivity().finish()
      startActivity(
        Intent(requireContext(), MainActivity::class.java)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          .putExtra(MainActivity.EXTRA_HOME_DESTINATION, R.id.account)
      )
    }

    val signInToken = requireNotNull(arguments?.getString(EXTRA_SIGN_IN_TOKEN)) {
      "signInToken extra is required to launch SignInLinkHandlerFragment"
    }

    viewModel.signInWithToken(signInToken)
    analyticsProvider?.setCurrentScreen(this::class)
  }

  companion object {

    private const val EXTRA_SIGN_IN_TOKEN = "signInToken"

    fun newInstance(signInToken: String): SignInLinkHandlerFragment {
      val fragment = SignInLinkHandlerFragment()
      fragment.arguments = bundleOf(EXTRA_SIGN_IN_TOKEN to signInToken)
      return fragment
    }
  }
}

@HiltViewModel
class SignInLinkHandlerViewModel @Inject constructor(
  private val accountRepository: AccountRepository,
) : ViewModel() {

  private val signInResource = MutableSharedFlow<Resource<Unit>>()

  val isSigningIn: StateFlow<Boolean> = signInResource.transform { r ->
    emit(r is Resource.Loading)
  }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

  val signInErrorStrRes: StateFlow<Int?> = signInResource.transform { r ->
    emit(
      when (r.error) {
        null -> null
        is NotSignedInError -> R.string.sign_in_token_error
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  internal fun signInWithToken(token: String) {
    if (isSigningIn.value) {
      return
    }

    viewModelScope.launch {
      accountRepository.signInWithToken(token).collect(signInResource)
    }
  }
}

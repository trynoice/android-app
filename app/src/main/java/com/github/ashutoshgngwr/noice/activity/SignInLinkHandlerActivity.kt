package com.github.ashutoshgngwr.noice.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SignInLinkHandlerActivityBinding
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import com.github.ashutoshgngwr.noice.repository.Resource
import com.github.ashutoshgngwr.noice.repository.errors.NetworkError
import com.github.ashutoshgngwr.noice.repository.errors.NotSignedInError
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SignInLinkHandlerActivity : AppCompatActivity() {

  private lateinit var binding: SignInLinkHandlerActivityBinding
  private val viewModel: SignInLinkHandlerViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val isSupportedUri = intent.dataString?.startsWith("https://trynoice.com/sign-in") == true
    val signInToken = intent.data?.getQueryParameter("token")
    if (Intent.ACTION_VIEW != intent.action || !isSupportedUri || signInToken == null) {
      finish()
      return
    }

    binding = SignInLinkHandlerActivityBinding.inflate(layoutInflater)
    binding.lifecycleOwner = this
    binding.viewModel = viewModel
    binding.dismiss.setOnClickListener { finish() }
    binding.continuu.setOnClickListener { finishAndLaunchMainActivity() }

    setContentView(binding.root)
    viewModel.signInWithToken(signInToken)
  }

  private fun finishAndLaunchMainActivity() {
    finish()
    startActivity(
      Intent(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .putExtra(MainActivity.EXTRA_NAV_DESTINATION, R.id.home_account)
    )
  }
}

@HiltViewModel
class SignInLinkHandlerViewModel @Inject constructor(
  private val accountRepository: AccountRepository,
) : ViewModel() {

  private val signInResource = MutableStateFlow<Resource<Unit>>(Resource.Loading())

  val isSigningIn: StateFlow<Boolean> = signInResource.transform { r ->
    emit(r is Resource.Loading)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

  val signInErrorStrRes: StateFlow<Int?> = signInResource.transform { r ->
    emit(
      when (r.error) {
        null -> null
        is NotSignedInError -> R.string.sign_in_token_error
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  internal fun signInWithToken(token: String) {
    if (isSigningIn.value) {
      return
    }

    viewModelScope.launch {
      accountRepository.signInWithToken(token)
        .flowOn(Dispatchers.IO)
        .collect(signInResource)
    }
  }
}

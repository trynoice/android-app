package com.github.ashutoshgngwr.noice.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.databinding.SignInLinkHandlerActivityBinding
import com.github.ashutoshgngwr.noice.model.NetworkError
import com.github.ashutoshgngwr.noice.model.NotSignedInError
import com.github.ashutoshgngwr.noice.repository.AccountRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    setContentView(binding.root)

    viewModel.onFailureButtonClick = { finish() }
    viewModel.onSuccessButtonClick = {
      finish()
      startActivity(
        Intent(this, MainActivity::class.java)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          .putExtra(MainActivity.EXTRA_NAV_DESTINATION, R.id.home_account)
      )
    }

    viewModel.signInWithToken(signInToken)
  }
}

@HiltViewModel
class SignInLinkHandlerViewModel @Inject constructor(
  private val accountRepository: AccountRepository,
) : ViewModel() {

  var onSuccessButtonClick: () -> Unit = {}
  var onFailureButtonClick: () -> Unit = {}
  val isSigningIn = MutableStateFlow(false)
  val isSignInComplete = MutableStateFlow(false)
  val signInError = MutableStateFlow<Throwable?>(null)
  val signInErrorStringRes: StateFlow<Int?> = signInError.transform { error ->
    emit(
      when (error) {
        null -> null
        is NotSignedInError -> R.string.sign_in_token_error
        is NetworkError -> R.string.network_error
        else -> R.string.unknown_error
      }
    )
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

  internal fun signInWithToken(token: String) {
    if (isSigningIn.value || isSignInComplete.value) {
      return
    }

    viewModelScope.launch(Dispatchers.IO) {
      signInError.emit(null)
      isSigningIn.emit(true)

      try {
        accountRepository.signInWithToken(token)
        isSignInComplete.emit(true)
      } catch (e: Throwable) {
        signInError.emit(e)
      } finally {
        isSigningIn.emit(false)
      }
    }
  }
}

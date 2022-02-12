package com.github.ashutoshgngwr.noice.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
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
  val isSigningIn = MutableLiveData(false)
  val isSignInComplete = MutableLiveData(false)
  val signInError = MutableLiveData<Throwable?>()
  val signInErrorStringRes = MediatorLiveData<Int?>().also {
    it.addSource(signInError) { e ->
      it.postValue(
        when (e) {
          null -> null
          is NotSignedInError -> R.string.sign_in_token_error
          is NetworkError -> R.string.network_error
          else -> R.string.unknown_error
        }
      )
    }
  }

  internal fun signInWithToken(token: String) {
    if (isSigningIn.value == true || isSignInComplete.value == true) {
      return
    }

    signInError.postValue(null)
    isSigningIn.postValue(true)
    viewModelScope.launch(Dispatchers.IO) {
      try {
        accountRepository.signInWithToken(token)
        isSignInComplete.postValue(true)
      } catch (e: Throwable) {
        signInError.postValue(e)
      } finally {
        isSigningIn.postValue(false)
      }
    }
  }
}

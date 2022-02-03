package com.github.ashutoshgngwr.noice.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.liveData
import com.github.ashutoshgngwr.noice.model.Resource
import com.trynoice.api.client.NoiceApiClient
import com.trynoice.api.client.models.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
  private val apiClient: NoiceApiClient,
) {

  fun isSignedIn(): LiveData<Boolean> = apiClient.getSignedInState().asLiveData()

  fun getProfile(): LiveData<Resource<Profile>> = liveData {
    if (apiClient.isSignedIn()) {
      emit(Resource.loading())
      withContext(Dispatchers.IO) {
        try {
          val profile = apiClient.accounts().getProfile()
          emit(Resource.success(profile))
        } catch (e: IOException) {
          emit(Resource.error(Resource.NetworkError))
          Log.i(LOG_TAG, "network error when loading profile", e)
        } catch (e: Throwable) {
          emit(Resource.error(Resource.UnknownError))
          Log.i(LOG_TAG, "unknown error when loading profile", e)
        }
      }
    } else {
      emit(Resource.error(NotSignedInError))
    }
  }

  companion object {
    private val LOG_TAG = AccountRepository::class.simpleName
  }

  object NotSignedInError : Throwable()
}

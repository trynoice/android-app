package com.trynoice.api.client

import android.content.Context
import android.content.SharedPreferences
import com.trynoice.api.client.models.AuthCredentials

/**
 * A helper class to manage [SharedPreferences] that stores refresh and access tokens.
 */
internal class AuthCredentialRepository(context: Context) {

  private val prefs = context.getSharedPreferences(CREDENTIAL_STORE_NAME, Context.MODE_PRIVATE)

  /**
   * @return refresh token if present, `null` otherwise.
   */
  fun getRefreshToken(): String? {
    return prefs.getString(REFRESH_TOKEN_KEY, null)
  }

  /**
   * @return access token if present, `null` otherwise.
   */
  fun getAccessToken(): String? {
    return prefs.getString(ACCESS_TOKEN_KEY, null)
  }

  /**
   * Persists [credentials] to the underlying permanent storage.
   */
  fun setCredentials(credentials: AuthCredentials) {
    prefs.edit()
      .putString(REFRESH_TOKEN_KEY, credentials.refreshToken)
      .putString(ACCESS_TOKEN_KEY, credentials.accessToken)
      .apply()
  }

  /**
   * Clears all saved credentials from the underlying permanent storage.
   */
  fun clearCredentials() {
    prefs.edit().clear().apply()
  }

  companion object {
    private const val CREDENTIAL_STORE_NAME = "com.trynoice.api.client.auth.credentials"
    private const val REFRESH_TOKEN_KEY = "refresh-token"
    private const val ACCESS_TOKEN_KEY = "access-token"
  }
}

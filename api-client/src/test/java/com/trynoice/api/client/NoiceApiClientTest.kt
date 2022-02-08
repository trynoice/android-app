package com.trynoice.api.client

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.trynoice.api.client.auth.AuthCredentialRepository
import com.trynoice.api.client.models.AuthCredentials
import com.trynoice.api.client.models.Profile
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class NoiceApiClientTest {

  private lateinit var gson: Gson
  private lateinit var mockServer: MockWebServer
  private lateinit var credentialRepository: AuthCredentialRepository
  private lateinit var apiClient: NoiceApiClient

  @Before
  fun setUp() {
    gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
    mockServer = MockWebServer()
    mockServer.start()

    val context = ApplicationProvider.getApplicationContext<Context>()
    credentialRepository = AuthCredentialRepository(context)
    apiClient = NoiceApiClient(context, gson, mockServer.url("").toString())
  }

  @After
  fun teardown() {
    mockServer.shutdown()
    credentialRepository.clearCredentials()
  }

  @Test
  fun signInWithToken() = runTest {
    val testCredentials = AuthCredentials("test-rt", "test-at")
    mockServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(gson.toJson(testCredentials))
    )

    val testSignInToken = "test-rt"
    apiClient.signInWithToken(testSignInToken)
    runCatching { mockServer.takeRequest() }
      .onSuccess { assertEquals(testSignInToken, it.getHeader("X-Refresh-Token")) }

    assertEquals(true, apiClient.getSignedInState().lastOrNull())
    assertEquals(testCredentials.refreshToken, credentialRepository.getRefreshToken())
    assertEquals(testCredentials.accessToken, credentialRepository.getAccessToken())
  }

  @Test
  fun accessTokenInjection() = runTest {
    val testAccessToken = "test-at"
    credentialRepository.setCredentials(AuthCredentials("test-rt", testAccessToken))

    val testProfile = Profile(1, "test@api-client.test", "Test User")
    mockServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(gson.toJson(testProfile))
    )

    runCatching { apiClient.accounts().getProfile() }
      .onSuccess { assertEquals(testProfile, it) }

    runCatching { mockServer.takeRequest() }
      .onSuccess { assertEquals("Bearer $testAccessToken", it.getHeader("Authorization")) }
  }

  @Test
  fun accessTokenInjection_withExpiredAccessToken() = runTest {
    val testProfile = Profile(1, "test@api-client.test", "Test User")
    val oldCredentials = AuthCredentials("old-rt", "old-at")
    val newCredentials = AuthCredentials("new-rt", "new-at")
    credentialRepository.setCredentials(oldCredentials)

    mockServer.enqueue(MockResponse().setResponseCode(401))
    mockServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(gson.toJson(newCredentials))
    )

    mockServer.enqueue(
      MockResponse()
        .setResponseCode(200)
        .setBody(gson.toJson(testProfile))
    )

    runCatching { apiClient.accounts().getProfile() }
      .onSuccess { assertEquals(testProfile, it) }

    runCatching { mockServer.takeRequest() }
      .onSuccess {
        assertEquals("/v1/accounts/profile", it.path)
        assertEquals("Bearer ${oldCredentials.accessToken}", it.getHeader("Authorization"))
      }

    runCatching { mockServer.takeRequest() }
      .onSuccess {
        assertEquals("/v1/accounts/credentials", it.path)
        assertEquals(oldCredentials.refreshToken, it.getHeader("X-Refresh-Token"))
      }

    runCatching { mockServer.takeRequest() }
      .onSuccess {
        assertEquals("/v1/accounts/profile", it.path)
        assertEquals("Bearer ${newCredentials.accessToken}", it.getHeader("Authorization"))
      }
  }

  @Test
  fun signOut() = runTest {
    val testCredentials = AuthCredentials("test-rt", "test-at")
    credentialRepository.setCredentials(testCredentials)

    mockServer.enqueue(MockResponse().setResponseCode(200))
    runCatching { apiClient.signOut() }
    runCatching { mockServer.takeRequest() }
      .onSuccess {
        assertEquals("/v1/accounts/signOut", it.path)
        assertEquals(testCredentials.refreshToken, it.getHeader("X-Refresh-Token"))
      }

    assertEquals(false, apiClient.getSignedInState().lastOrNull())
    assertNull(credentialRepository.getRefreshToken())
    assertNull(credentialRepository.getAccessToken())
  }
}

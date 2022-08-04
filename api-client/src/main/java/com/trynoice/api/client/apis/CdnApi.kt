package com.trynoice.api.client.apis

import com.trynoice.api.client.auth.annotations.NeedsAccessToken
import com.trynoice.api.client.models.LibraryManifest
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * APIs to fetch resources from the CDN. The client caches CDN responses if and as directed by the
 * `Cache-Control` response headers.
 */
interface CdnApi {

  /**
   * Retrieves the [LibraryManifest] from the CDN.
   *
   * Responses:
   * - 200: library manifest download successful.
   * - 500: internal server error.
   *
   * @throws retrofit2.HttpException on HTTP errors.
   * @throws java.io.IOException on network errors.
   */
  @GET("/library/library-manifest.json")
  suspend fun libraryManifest(): LibraryManifest

  /**
   * Retrieves a protected or public resource from the CDN and returns it as a [Streaming]
   * [Response].
   *
   * Responses:
   * - 200: if the resource is found.
   * - 401: if the resource is found, but the user needs to be authenticated.
   * - 403: if the resource is found, but the user doesn't have access to it.
   * - 404: if the resource isn't found.
   * - 500: on internal server errors.
   *
   * @param resourcePath absolute path of the resource on the CDN server.
   */
  @NeedsAccessToken
  @Streaming
  @GET
  fun resource(@Url resourcePath: String): Call<ResponseBody>

  /**
   * Retrieves the metadata of a protected or public resource from the CDN and returns it as a
   * [Response].
   *
   * Responses:
   * - 200: if the resource is found.
   * - 401: if the resource is found, but the user needs to be authenticated.
   * - 403: if the resource is found, but the user doesn't have access to it.
   * - 404: if the resource isn't found.
   * - 500: on internal server errors.
   *
   * @param resourcePath absolute path of the resource on the CDN server.
   */
  @NeedsAccessToken
  @HEAD
  suspend fun resourceMetadata(@Url resourcePath: String): Response<Void>
}

package com.trynoice.api.client.apis

import com.trynoice.api.client.models.LibraryManifest
import retrofit2.http.GET

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
}

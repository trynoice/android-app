package com.github.ashutoshgngwr.noice.model

/**
 * A wrapper class for holding [Loading], [Success] or [Failure] state of an operation along with
 * its result [data] and [error].
 */
sealed class Resource<T : Any> private constructor(val data: T?, val error: Throwable?) {

  /**
   * Indicates that the operation is processing.
   *
   * @param data data that the running operation has been able to load so far. It may be `null`.
   */
  class Loading<T : Any>(data: T? = null) : Resource<T>(data, null)

  /**
   * Indicates that the operation has succeeded.
   *
   * @param data must not be `null`.
   */
  class Success<T : Any>(data: T) : Resource<T>(data, null)

  /**
   * Indicates that the operation has failed.
   *
   * @param error must not be `null`.
   * @param data any data that was returned by the failing operation. It may be `null`
   */
  class Failure<T : Any>(error: Throwable, data: T? = null) : Resource<T>(data, error)
}

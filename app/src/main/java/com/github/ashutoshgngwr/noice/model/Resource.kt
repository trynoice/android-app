package com.github.ashutoshgngwr.noice.model

/**
 * A generic class that holds a value with its loading status.
 */
class Resource<out T> private constructor(
  val status: Status,
  val data: T? = null,
  val error: Throwable? = null,
) {

  companion object {
    fun <T> success(data: T? = null): Resource<T> {
      return Resource(Status.SUCCESS, data = data)
    }

    fun <T> error(type: Throwable): Resource<T> {
      return Resource(Status.ERROR, error = type)
    }

    fun <T> loading(): Resource<T> {
      return Resource(Status.LOADING)
    }
  }

  enum class Status {
    SUCCESS, ERROR, LOADING
  }

  object NetworkError : Throwable()
  object UnknownError : Throwable()
}


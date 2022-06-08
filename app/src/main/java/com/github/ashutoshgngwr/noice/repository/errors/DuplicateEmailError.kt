package com.github.ashutoshgngwr.noice.repository.errors

/**
 * Thrown by update profile operation in account repository when another account with the updated
 * email already exists.
 */
object DuplicateEmailError : Throwable()

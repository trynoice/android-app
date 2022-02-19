package com.github.ashutoshgngwr.noice.model

/**
 * Thrown by network-backed data repository operations when network is unreachable.
 */
object NetworkError : Throwable()

/**
 * Thrown by repository operations that require the api client to be signed-in.
 */
object NotSignedInError : Throwable()

/**
 * Thrown by sign-in and sign-up operations in account repository when a user account is temporarily
 * blocked from making sign-in attempts.
 *
 * @param timeoutSeconds the duration (in seconds) that clients should wait before making another
 * attempt.
 */
class AccountTemporarilyLockedError(val timeoutSeconds: Int) : Throwable()

/**
 * Thrown by update profile operation in account repository when another account with the updated
 * email already exists.
 */
object DuplicateEmailError : Throwable()

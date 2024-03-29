package com.github.ashutoshgngwr.noice.repository.errors

/**
 * Thrown by sign-in and sign-up operations in account repository when a user account is temporarily
 * blocked from making sign-in attempts.
 *
 * @param timeoutSeconds the duration (in seconds) that clients should wait before making another
 * attempt.
 */
class AccountTemporarilyLockedError(val timeoutSeconds: Int) : Throwable()

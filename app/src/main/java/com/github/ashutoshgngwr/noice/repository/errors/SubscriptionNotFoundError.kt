package com.github.ashutoshgngwr.noice.repository.errors

/**
 * Thrown by get operation in subscription repository when the subscription with the requested id
 * doesn't exist.
 */
object SubscriptionNotFoundError : Exception()

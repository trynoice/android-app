package com.github.ashutoshgngwr.noice.model


object NetworkError : Throwable()

object NotSignedInError : Throwable()

class AccountTemporarilyLockedError(val timeoutSeconds: Int) : Throwable()

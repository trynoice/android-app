package com.github.ashutoshgngwr.noice.model

import java.io.Serializable

/**
 * Represents the state of a sound's player instance at any given time.
 */
data class PlayerState(val soundId: String, val volume: Int) : Serializable

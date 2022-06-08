package com.trynoice.api.client

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type
import java.util.*

/**
 * A [JsonDeserializer] implementation that deserializes epoch milliseconds ([Long]) to
 * [java.util.Date].
 */
class EpochMillisToDateDeserializer : JsonDeserializer<Date> {

  override fun deserialize(e: JsonElement?, t: Type?, ctx: JsonDeserializationContext?): Date? {
    return e?.asLong?.let { Date(it) }
  }
}

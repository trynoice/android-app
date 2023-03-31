package com.github.ashutoshgngwr.noice.cast

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class EventDeserializer : JsonDeserializer<Event> {

  override fun deserialize(
    json: JsonElement,
    typeOfT: Type?,
    context: JsonDeserializationContext
  ): Event {
    return when (val kind = json.asJsonObject?.get("kind")?.asString) {
      "GetAccessToken" -> context.deserialize(json, GetAccessTokenEvent::class.java)
      "SoundStateChanged" -> context.deserialize(json, SoundStateChangedEvent::class.java)
      else -> throw JsonParseException("unknown event kind: $kind")
    }
  }
}

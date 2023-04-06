package com.github.ashutoshgngwr.noice.cast

import android.os.Handler
import com.github.ashutoshgngwr.noice.cast.models.Event
import com.github.ashutoshgngwr.noice.cast.models.EventDeserializationRegistry
import com.google.android.gms.cast.Cast.MessageReceivedCallback
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.framework.CastContext
import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

/**
 * A bi-directional messaging channel for messages with [Event] as the base class. It facilitates
 * communication among the connected device on the current cast session in the given [castContext].
 */
class CastMessagingChannel(
  private val castContext: CastContext,
  private val namespace: String,
  gson: Gson,
  private val handler: Handler,
) : MessageReceivedCallback {

  private val gson = gson.newBuilder()
    .registerTypeAdapter(Event::class.java, EventDeserializer())
    .create()

  private val eventListeners = mutableSetOf<EventListener>()

  /**
   * Sends an outgoing event.
   */
  fun send(event: Event) {
    castContext.sessionManager.currentCastSession?.sendMessage(namespace, gson.toJson(event))
  }

  /**
   * Adds a listener to receive events.
   */
  fun addEventListener(listener: EventListener) {
    if (eventListeners.isEmpty()) {
      castContext.sessionManager.currentCastSession?.setMessageReceivedCallbacks(namespace, this)
    }

    eventListeners.add(listener)
  }

  /**
   * Removes a previously registered [EventListener].
   */
  fun removeEventListener(listener: EventListener) {
    eventListeners.remove(listener)
    if (eventListeners.isEmpty()) {
      castContext.sessionManager.currentCastSession?.removeMessageReceivedCallbacks(namespace)
    }
  }

  override fun onMessageReceived(device: CastDevice, namespace: String, message: String) {
    val event = gson.fromJson(message, Event::class.java)
    eventListeners.forEach { listener ->
      handler.post { listener.onEventReceived(event) }
    }
  }

  /**
   * A listener interface to receive events from a [CastMessagingChannel].
   */
  interface EventListener {
    fun onEventReceived(event: Event)
  }

  /**
   * A conditional deserializer for [Event] sub-classes. It checks the `kind` property of the event
   * and deserializes the input json to its corresponding sub-class type.
   */
  private class EventDeserializer : JsonDeserializer<Event> {

    override fun deserialize(
      json: JsonElement,
      typeOfT: Type?,
      context: JsonDeserializationContext
    ): Event {
      val kind = json.asJsonObject
        ?.get("kind")
        ?.asString
        ?: throw JsonParseException("object `kind` is null")

      val type = EventDeserializationRegistry.getTypeForKind(kind)
      return context.deserialize(json, type)
    }
  }
}

package com.github.ashutoshgngwr.noice.cast

import android.os.Handler
import com.github.ashutoshgngwr.noice.cast.models.Event
import com.google.android.gms.cast.Cast.MessageReceivedCallback
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.framework.CastContext
import com.google.gson.Gson

/**
 * A bi-directional messaging channel for the connected device on the current session in the given
 * [castContext].
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
   * Sends an outgoing message.
   */
  fun send(event: Event) {
    castContext.sessionManager.currentCastSession?.sendMessage(namespace, gson.toJson(event))
  }

  /**
   * Adds a listener to receive messages.
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
}

package com.github.ashutoshgngwr.noice.cast

import com.google.android.gms.cast.Cast.MessageReceivedCallback
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.framework.CastContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * A bi-directional messaging channel for the connected device on the current session in the given
 * [castContext]. Both [OutgoingMessage]s and [IncomingMessage]s are sent and received as JSON
 * strings. The channel internally encodes/decodes these messages using the given [Gson] instance.
 */
class CastMessagingChannel<OutgoingMessage : Any, IncomingMessage : Any>(
  private val castContext: CastContext,
  private val namespace: String,
  private val gson: Gson,
) : MessageReceivedCallback {

  private val messageListeners = mutableSetOf<MessageListener<IncomingMessage>>()
  private val incomingMessageType = object : TypeToken<IncomingMessage>() {}.type

  /**
   * Sends an outgoing message.
   */
  fun send(message: OutgoingMessage) {
    castContext.sessionManager.currentCastSession?.sendMessage(namespace, gson.toJson(message))
  }

  /**
   * Adds a listener to receive messages.
   */
  fun addListener(listener: MessageListener<IncomingMessage>) {
    if (messageListeners.isEmpty()) {
      castContext.sessionManager.currentCastSession?.setMessageReceivedCallbacks(namespace, this)
    }

    messageListeners.add(listener)
  }

  /**
   * Removes a listener registered using [addListener].
   */
  fun removeListener(listener: MessageListener<IncomingMessage>) {
    messageListeners.remove(listener)
    if (messageListeners.isEmpty()) {
      castContext.sessionManager.currentCastSession?.removeMessageReceivedCallbacks(namespace)
    }
  }

  override fun onMessageReceived(device: CastDevice, namespace: String, message: String) {
    val m = gson.fromJson<IncomingMessage>(message, incomingMessageType)
    messageListeners.forEach { it.onMessageReceived(m) }
  }

  /**
   * A listener interface to receive messages from a [CastMessagingChannel].
   */
  interface MessageListener<T : Any> {
    fun onMessageReceived(message: T)
  }
}

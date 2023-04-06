package com.github.ashutoshgngwr.noice.cast

import android.os.Handler
import android.os.Looper
import com.github.ashutoshgngwr.noice.cast.models.Event
import com.github.ashutoshgngwr.noice.cast.models.EventDeserializationRegistry
import com.google.android.gms.cast.Cast.MessageReceivedCallback
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.gson.Gson
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper
import javax.inject.Inject

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
class CastMessagingChannelTest {

  @get:Rule
  val hiltRule = HiltAndroidRule(this)

  @set:Inject
  internal lateinit var gson: Gson

  private val namespace = "test-ns"
  private lateinit var castSessionMock: CastSession
  private lateinit var channel: CastMessagingChannel

  @Before
  fun setUp() {
    hiltRule.inject()

    castSessionMock = mockk(relaxed = true)
    val contextMock = mockk<CastContext> {
      every { sessionManager } returns mockk {
        every { currentCastSession } returns castSessionMock
      }
    }

    channel = CastMessagingChannel(contextMock, namespace, gson, Handler(Looper.getMainLooper()))
  }

  @Test
  fun send() {
    mapOf(
      TestEvent("test-payload-1") to """{"payload":"test-payload-1","kind":"Test"}""",
      TestEvent("test-payload-2") to """{"payload":"test-payload-2","kind":"Test"}""",
      TestEvent("test-payload-3") to """{"payload":"test-payload-3","kind":"Test"}""",
    ).forEach { (event, expectedMessage) ->
      channel.send(event)
      verify(exactly = 1) { castSessionMock.sendMessage(namespace, expectedMessage) }
    }

  }

  @Test
  fun receive() {
    EventDeserializationRegistry.registerType("Test", TestEvent::class.java)
    val mockListener = mockk<CastMessagingChannel.EventListener>(relaxed = true)
    channel.addEventListener(mockListener)

    val messageCallbackSlot = slot<MessageReceivedCallback>()
    verify(exactly = 1) {
      castSessionMock.setMessageReceivedCallbacks(namespace, capture(messageCallbackSlot))
    }

    assertNotNull(messageCallbackSlot.captured)

    mapOf(
      """{"payload":"test-payload-1","kind":"Test"}""" to TestEvent("test-payload-1"),
      """{"payload":"test-payload-2","kind":"Test"}""" to TestEvent("test-payload-2"),
      """{"payload":"test-payload-3","kind":"Test"}""" to TestEvent("test-payload-3"),
    ).forEach { (message, expectedEvent) ->
      messageCallbackSlot.captured.onMessageReceived(mockk(), namespace, message)
      ShadowLooper.idleMainLooper()
      verify(exactly = 1) { mockListener.onEventReceived(expectedEvent) }
    }

    clearMocks(mockListener)
    channel.removeEventListener(mockListener)
    messageCallbackSlot.captured.onMessageReceived(
      mockk(),
      namespace,
      """{"payload":"test-payload-4","kind":"Test"}"""
    )

    ShadowLooper.idleMainLooper()
    verify(exactly = 0) { mockListener.onEventReceived(any()) }
  }

  data class TestEvent(
    val payload: String,
    override val kind: String = "Test",
  ) : Event()
}

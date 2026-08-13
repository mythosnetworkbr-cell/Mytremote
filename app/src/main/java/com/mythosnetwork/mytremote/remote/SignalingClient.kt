package com.mythosnetwork.mytremote.remote

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Small, lifecycle-safe signaling transport. Media itself is intended to use WebRTC;
 * this channel only exchanges pairing and SDP/ICE messages.
 */
class SignalingClient(
    private val endpoint: String,
    private val deviceId: String,
    private val deviceCode: String,
    private val listener: Listener
) {
    interface Listener {
        fun onConnected()
        fun onRegistered()
        fun onIncomingRequest(requesterCode: String, requesterId: String, sessionToken: String)
        fun onAccepted(sessionToken: String)
        fun onRejected(sessionToken: String)
        fun onSignal(message: JSONObject)
        fun onError(message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private var socket: WebSocket? = null

    fun connect() {
        val request = Request.Builder().url(endpoint).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                main.post {
                    listener.onConnected()
                    send(JSONObject().put("type", "REGISTER")
                        .put("deviceId", deviceId)
                        .put("deviceCode", deviceCode))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    when (msg.optString("type")) {
                        "REGISTERED" -> main.post { listener.onRegistered() }
                        "INCOMING_REQUEST" -> main.post {
                            listener.onIncomingRequest(
                                msg.optString("requesterCode"),
                                msg.optString("requesterId"),
                                msg.optString("sessionToken")
                            )
                        }
                        "ACCEPT" -> main.post { listener.onAccepted(msg.optString("sessionToken")) }
                        "REJECT" -> main.post { listener.onRejected(msg.optString("sessionToken")) }
                        "OFFER", "ANSWER", "ICE" -> main.post { listener.onSignal(msg) }
                        "ERROR" -> main.post { listener.onError(msg.optString("message", "unknown_error")) }
                    }
                } catch (e: Exception) {
                    main.post { listener.onError("invalid_signaling_message") }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                main.post { listener.onError(t.message ?: "signaling_connection_failed") }
            }
        })
    }

    fun request(targetCode: String) = send(JSONObject()
        .put("type", "REQUEST")
        .put("targetCode", targetCode.trim().uppercase()))

    fun accept(targetId: String, sessionToken: String) = send(JSONObject()
        .put("type", "ACCEPT")
        .put("targetId", targetId)
        .put("sessionToken", sessionToken))

    fun reject(targetId: String, sessionToken: String) = send(JSONObject()
        .put("type", "REJECT")
        .put("targetId", targetId)
        .put("sessionToken", sessionToken))

    fun sendSignal(type: String, targetId: String, sessionToken: String, payload: JSONObject) {
        send(JSONObject(payload.toString())
            .put("type", type)
            .put("targetId", targetId)
            .put("sessionToken", sessionToken))
    }

    private fun send(message: JSONObject) {
        socket?.send(message.toString())
    }

    fun close() {
        socket?.close(1000, "session_end")
        socket = null
        client.dispatcher.executorService.shutdown()
    }
}

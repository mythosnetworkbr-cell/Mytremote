package com.mythosnetwork.mytremote.remote

@JvmInline
value class DeviceId(val value: String)

enum class SessionStatus {
    IDLE, CONNECTING, WAITING_AUTHORIZATION, CONNECTED, DISCONNECTED, ERROR
}

data class RemoteDevice(
    val id: DeviceId,
    val name: String,
    val model: String,
    val online: Boolean,
    val authorized: Boolean = false
)

data class RemoteSession(
    val localDevice: DeviceId,
    val remoteDevice: DeviceId,
    val status: SessionStatus = SessionStatus.IDLE,
    val errorMessage: String? = null
)

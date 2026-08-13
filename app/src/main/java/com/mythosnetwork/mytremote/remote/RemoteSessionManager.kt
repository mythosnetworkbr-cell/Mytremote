package com.mythosnetwork.mytremote.remote

class RemoteSessionManager(
    private val signaling: SignalingClient = NoOpSignalingClient()
) {
    var session: RemoteSession? = null
        private set

    suspend fun connect(localId: DeviceId, remoteId: DeviceId) {
        session = RemoteSession(localId, remoteId, SessionStatus.CONNECTING)
        // O transporte real será iniciado pela implementação WebRTC.
    }

    fun markAuthorized() {
        session = session?.copy(status = SessionStatus.CONNECTED)
    }

    fun fail(message: String) {
        session = session?.copy(status = SessionStatus.ERROR, errorMessage = message)
    }

    fun disconnect() {
        session = session?.copy(status = SessionStatus.DISCONNECTED)
        signaling.close()
    }
}

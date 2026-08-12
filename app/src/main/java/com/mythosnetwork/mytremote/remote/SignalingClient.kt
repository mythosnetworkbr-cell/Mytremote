package com.mythosnetwork.mytremote.remote

/**
 * Contrato da camada de sinalização. A implementação Firebase/WebSocket será
 * conectada aqui sem acoplar a UI ao transporte.
 */
interface SignalingClient {
    suspend fun publishOffer(sessionId: String, payload: String)
    suspend fun publishAnswer(sessionId: String, payload: String)
    suspend fun publishIceCandidate(sessionId: String, payload: String)
    suspend fun sendCommand(sessionId: String, command: RemoteCommand)
    fun close()
}

class NoOpSignalingClient : SignalingClient {
    override suspend fun publishOffer(sessionId: String, payload: String) = Unit
    override suspend fun publishAnswer(sessionId: String, payload: String) = Unit
    override suspend fun publishIceCandidate(sessionId: String, payload: String) = Unit
    override suspend fun sendCommand(sessionId: String, command: RemoteCommand) = Unit
    override fun close() = Unit
}

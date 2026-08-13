package com.mythosnetwork.mytremote

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/** Authorized local-LAN control channel. The device owner must accept each session. */
class LocalRemoteManager(private val onRequest: (String, Socket) -> Unit) {
    companion object {
        const val PORT = 45454
        private const val DISCOVERY_PORT = 45456
        private const val DISCOVERY_INTERVAL_MS = 3000L
    }

    private val executor = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var server: ServerSocket? = null
    @Volatile private var clientSocket: Socket? = null
    @Volatile private var clientWriter: PrintWriter? = null
    @Volatile private var deviceCode: String = ""
    @Volatile private var discoverySocket: DatagramSocket? = null

    fun start() {
        if (running) return
        running = true
        executor.execute {
            try {
                server = ServerSocket(PORT)
                while (running) {
                    val socket = server!!.accept()
                    executor.execute { handle(socket) }
                }
            } catch (_: Exception) { }
        }
        executor.execute { discoveryLoop() }
    }

    fun setDeviceCode(code: String) { deviceCode = code }

    private fun discoveryLoop() {
        try {
            discoverySocket = DatagramSocket(DISCOVERY_PORT).apply { broadcast = true; reuseAddress = true }
            val socket = discoverySocket!!
            socket.soTimeout = 1000
            var lastBroadcast = 0L
            val buffer = ByteArray(512)
            while (running) {
                val now = System.currentTimeMillis()
                if (deviceCode.isNotBlank() && now - lastBroadcast >= DISCOVERY_INTERVAL_MS) {
                    val data = "HELLO|$deviceCode".toByteArray(Charsets.UTF_8)
                    val packet = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT)
                    socket.send(packet)
                    lastBroadcast = now
                }
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    if (message.startsWith("FIND|")) {
                        val wanted = message.removePrefix("FIND|").trim()
                        if (wanted == deviceCode && deviceCode.isNotBlank()) {
                            val reply = "HERE|$deviceCode".toByteArray(Charsets.UTF_8)
                            socket.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                        }
                    }
                } catch (_: java.net.SocketTimeoutException) { }
            }
        } catch (_: Exception) { }
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 0
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val first = reader.readLine() ?: run { socket.close(); return }
            if (!first.startsWith("REQUEST|")) { socket.close(); return }
            val requester = first.removePrefix("REQUEST|").take(64)
            main.post { onRequest(requester, socket) }
            while (running && !socket.isClosed) {
                val line = reader.readLine() ?: break
                if (line.startsWith("CMD|")) handleCommand(line.removePrefix("CMD|"))
            }
        } catch (_: Exception) { }
        finally { try { socket.close() } catch (_: Exception) {} }
    }

    private fun handleCommand(command: String) {
        val p = command.split('|')
        when (p.firstOrNull()) {
            "BACK" -> RemoteAccessibilityService.back()
            "HOME" -> RemoteAccessibilityService.home()
            "RECENTS" -> RemoteAccessibilityService.recents()
            "TAP" -> if (p.size >= 3) RemoteAccessibilityService.tap(p[1].toFloatOrNull() ?: return, p[2].toFloatOrNull() ?: return)
            "SWIPE" -> if (p.size >= 5) RemoteAccessibilityService.swipe(
                p[1].toFloatOrNull() ?: return, p[2].toFloatOrNull() ?: return,
                p[3].toFloatOrNull() ?: return, p[4].toFloatOrNull() ?: return
            )
        }
    }

    fun accept(socket: Socket) {
        executor.execute {
            try { PrintWriter(socket.getOutputStream(), true).println("ACCEPT") }
            catch (_: Exception) { try { socket.close() } catch (_: Exception) {} }
        }
    }

    fun reject(socket: Socket) {
        executor.execute {
            try { PrintWriter(socket.getOutputStream(), true).println("REJECT") }
            finally { try { socket.close() } catch (_: Exception) {} }
        }
    }

    fun request(addressOrId: String, myCode: String, callback: (String) -> Unit) {
        executor.execute {
            try {
                val target = addressOrId.trim()
                val ip = if (target.startsWith("MYT-", ignoreCase = true)) discoverIp(target) else target
                if (ip.isNullOrBlank()) {
                    main.post { callback("ERROR:DEVICE_NOT_FOUND") }
                    return@execute
                }
                val socket = Socket(ip, PORT)
                socket.soTimeout = 15000
                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                writer.println("REQUEST|$myCode")
                val response = reader.readLine() ?: "NO_RESPONSE"
                if (response == "ACCEPT") {
                    clientSocket?.let { try { it.close() } catch (_: Exception) {} }
                    clientSocket = socket
                    clientWriter = writer
                    socket.soTimeout = 0
                } else {
                    try { socket.close() } catch (_: Exception) {}
                }
                main.post { callback(response) }
            } catch (e: Exception) { main.post { callback("ERROR:${e.javaClass.simpleName}") } }
        }
    }

    private fun discoverIp(targetId: String): String? {
        val socket = DatagramSocket().apply { broadcast = true; soTimeout = 2500; reuseAddress = true }
        return try {
            val data = "FIND|${targetId.uppercase()}".toByteArray(Charsets.UTF_8)
            socket.send(DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT))
            val buffer = ByteArray(512)
            val deadline = System.currentTimeMillis() + 2500
            while (System.currentTimeMillis() < deadline) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    if (message.equals("HERE|${targetId.uppercase()}", ignoreCase = true)) return packet.address.hostAddress
                } catch (_: java.net.SocketTimeoutException) { break }
            }
            null
        } finally { socket.close() }
    }

    fun sendCommand(command: String): Boolean {
        val writer = clientWriter ?: return false
        return try { writer.println("CMD|$command"); writer.checkError().not() } catch (_: Exception) { false }
    }

    fun disconnect() {
        try { clientSocket?.close() } catch (_: Exception) {}
        clientSocket = null
        clientWriter = null
    }

    fun stop() {
        running = false
        disconnect()
        try { server?.close() } catch (_: Exception) { }
        server = null
        try { discoverySocket?.close() } catch (_: Exception) { }
        discoverySocket = null
    }
}

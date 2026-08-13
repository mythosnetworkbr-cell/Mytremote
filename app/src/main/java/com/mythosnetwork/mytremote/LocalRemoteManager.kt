package com.mythosnetwork.mytremote

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/** Authorized local-LAN control channel. The device owner must accept each session. */
class LocalRemoteManager(private val onRequest: (String, Socket) -> Unit) {
    companion object { const val PORT = 45454 }
    private val executor = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var server: ServerSocket? = null
    @Volatile private var clientSocket: Socket? = null
    @Volatile private var clientWriter: PrintWriter? = null

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
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 0
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)
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

    fun request(ip: String, myCode: String, callback: (String) -> Unit) {
        executor.execute {
            try {
                val socket = Socket(ip.trim(), PORT)
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
    }
}

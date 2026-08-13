package com.mythosnetwork.mytremote

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/** Local-Wi-Fi pairing/authorization transport. Both phones must be on the same LAN. */
class LocalRemoteManager(private val context: Context, private val onRequest: (String, Socket) -> Unit) {
    companion object { const val PORT = 45454 }
    private val executor = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var server: ServerSocket? = null

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
            socket.soTimeout = 15000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val line = reader.readLine() ?: run { socket.close(); return }
            if (line.startsWith("REQUEST|")) {
                val requester = line.removePrefix("REQUEST|").take(64)
                main.post { onRequest(requester, socket) }
            } else socket.close()
        } catch (_: Exception) { try { socket.close() } catch (_: Exception) {} }
    }

    fun accept(socket: Socket) {
        executor.execute {
            try { PrintWriter(socket.getOutputStream(), true).println("ACCEPT"); socket.close() }
            catch (_: Exception) { }
        }
    }

    fun reject(socket: Socket) {
        executor.execute {
            try { PrintWriter(socket.getOutputStream(), true).println("REJECT"); socket.close() }
            catch (_: Exception) { }
        }
    }

    fun request(ip: String, myCode: String, callback: (String) -> Unit) {
        executor.execute {
            try {
                Socket(ip.trim(), PORT).use { socket ->
                    socket.soTimeout = 15000
                    PrintWriter(socket.getOutputStream(), true).println("REQUEST|$myCode")
                    val response = BufferedReader(InputStreamReader(socket.getInputStream())).readLine() ?: "NO_RESPONSE"
                    main.post { callback(response) }
                }
            } catch (e: Exception) { main.post { callback("ERROR:${e.javaClass.simpleName}") } }
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) { }
        server = null
    }
}

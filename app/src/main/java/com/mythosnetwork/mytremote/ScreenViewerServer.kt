package com.mythosnetwork.mytremote

import android.graphics.Bitmap
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.net.ServerSocket
import java.util.concurrent.Executors

class ScreenViewerServer(private val onFrame: (Bitmap) -> Unit) {
    companion object { const val PORT = 45455 }
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var running = false
    private var server: ServerSocket? = null

    fun start() {
        if (running) return
        running = true
        executor.execute {
            try {
                server = ServerSocket(PORT)
                while (running) {
                    val socket = server?.accept() ?: continue
                    executor.execute {
                        try {
                            DataInputStream(BufferedInputStream(socket.getInputStream())).use { input ->
                                while (running) {
                                    val size = input.readInt()
                                    if (size <= 0 || size > 8_000_000) break
                                    val bytes = ByteArray(size)
                                    input.readFully(bytes)
                                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, size)
                                    if (bitmap != null) onFrame(bitmap)
                                }
                            }
                        } catch (_: Exception) { } finally { try { socket.close() } catch (_: Exception) {} }
                    }
                }
            } catch (_: Exception) { }
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) { }
        server = null
    }
}

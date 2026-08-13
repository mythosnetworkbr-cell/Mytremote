package com.mythosnetwork.mytremote.remote

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors

/** Local-Wi-Fi signaling. No cloud account or Supabase is required. */
class RemoteApi(private val context: Context) {
    companion object { const val PORT = 45454 }
    private val prefs = context.getSharedPreferences("mythos_remote", Context.MODE_PRIVATE)
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var server: ServerSocket? = null

    var deviceId: String?
        get() = prefs.getString("device_id", null)
        private set(value) { prefs.edit().putString("device_id", value).apply() }
    var deviceCode: String?
        get() = prefs.getString("device_code", null)
        private set(value) { prefs.edit().putString("device_code", value).apply() }

    fun isLoggedIn() = true

    fun registerDevice(): String {
        val code = deviceCode ?: "MYT-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"
        if (deviceCode == null) deviceCode = code
        if (deviceId == null) deviceId = UUID.randomUUID().toString()
        prefs.edit().putString("device_name", "Mythøs Remote")
            .putString("model", "${Build.MANUFACTURER} ${Build.MODEL}").apply()
        return code
    }

    fun localIp(): String = try {
        NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.hostAddress?.startsWith("169.254") != true }
            ?.hostAddress ?: "Não encontrado"
    } catch (_: Exception) { "Não encontrado" }

    fun startLanServer(onRequest: (requester: JSONObject, reply: (Boolean) -> Unit) -> Unit) {
        if (server != null) return
        executor.execute {
            try {
                server = ServerSocket(PORT)
                while (!server!!.isClosed) {
                    val socket = server!!.accept()
                    executor.execute { handle(socket, onRequest) }
                }
            } catch (_: Exception) { }
        }
    }

    private fun handle(socket: Socket, onRequest: (JSONObject, (Boolean) -> Unit) -> Unit) {
        socket.use {
            val reader = BufferedReader(InputStreamReader(it.getInputStream()))
            val writer = PrintWriter(OutputStreamWriter(it.getOutputStream()), true)
            val line = reader.readLine() ?: return
            val msg = JSONObject(line)
            if (msg.optString("type") != "REQUEST") return
            val requester = msg.put("target", deviceCode ?: registerDevice())
            onRequest(requester) { accepted ->
                writer.println(JSONObject().put("type", if (accepted) "ACCEPT" else "REJECT"))
            }
        }
    }

    fun requestConnection(ip: String, onResult: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                Socket(ip.trim(), PORT).use { socket ->
                    val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    writer.println(JSONObject().put("type", "REQUEST")
                        .put("requesterCode", deviceCode ?: registerDevice())
                        .put("requesterModel", "${Build.MANUFACTURER} ${Build.MODEL}"))
                    val reply = reader.readLine()
                    val accepted = reply != null && JSONObject(reply).optString("type") == "ACCEPT"
                    onResult(accepted, if (accepted) "Conexão aceita" else "Conexão recusada")
                }
            } catch (e: Exception) { onResult(false, "Não foi possível conectar: ${e.message ?: "verifique o IP e o Wi-Fi"}") }
        }
    }

    fun stopLanServer() { try { server?.close() } catch (_: Exception) {}; server = null }
}

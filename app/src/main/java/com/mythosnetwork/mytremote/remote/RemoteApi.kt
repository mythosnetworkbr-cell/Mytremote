package com.mythosnetwork.mytremote.remote

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private const val SUPABASE_URL = "https://rcjexjhziwcynsjmcdap.supabase.co"
private const val SUPABASE_KEY = "sb_publishable_OglouaI2szEvKmNvK3HEUQ_IOuHv-V-"

class RemoteApi(private val context: Context) {
    private val prefs = context.getSharedPreferences("mythos_remote", Context.MODE_PRIVATE)
    private var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) { prefs.edit().putString("access_token", value).apply() }
    var deviceId: String?
        get() = prefs.getString("device_id", null)
        private set(value) { prefs.edit().putString("device_id", value).apply() }
    var deviceCode: String?
        get() = prefs.getString("device_code", null)
        private set(value) { prefs.edit().putString("device_code", value).apply() }

    fun isLoggedIn() = !accessToken.isNullOrBlank()

    fun login(email: String, password: String) {
        val json = request("/auth/v1/token?grant_type=password", "POST", JSONObject().apply {
            put("email", email); put("password", password)
        })
        accessToken = json.getString("access_token")
        registerDevice()
    }

    fun signup(email: String, password: String) {
        val json = request("/auth/v1/signup", "POST", JSONObject().apply {
            put("email", email); put("password", password)
        })
        if (json.has("access_token") && !json.isNull("access_token")) {
            accessToken = json.getString("access_token")
            registerDevice()
        }
    }

    fun registerDevice(): String {
        val token = accessToken ?: error("Faça login primeiro")
        val code = deviceCode ?: "MYT-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"
        val body = JSONObject().apply {
            put("user_id", JSONObject.NULL)
            put("device_code", code)
            put("device_name", "Mythøs Remote")
            put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("online", true)
        }
        val payload = body.toString().replace("\"user_id\":null,", "")
        val result = request("/rest/v1/remote_devices?on_conflict=device_code", "POST", JSONArray("[$payload]"), token, "resolution=merge-duplicates,return=representation")
        val obj = result.optJSONObject(0) ?: error("Não foi possível registrar o dispositivo")
        deviceId = obj.getString("id")
        deviceCode = obj.getString("device_code")
        return deviceCode!!
    }

    fun findDevice(code: String): JSONObject? {
        val token = accessToken ?: return null
        val arr = requestArray("/rest/v1/remote_devices?device_code=eq.${code.trim()}&select=id,device_code,device_name,model,online", "GET", null, token)
        return arr.optJSONObject(0)
    }

    fun createSession(targetDeviceId: String): String {
        val token = accessToken ?: error("Faça login primeiro")
        val local = deviceId ?: registerDevice()
        val arr = request("/rest/v1/remote_sessions", "POST", JSONArray("[{\"requester_device_id\":\"$local\",\"target_device_id\":\"$targetDeviceId\",\"status\":\"pending\"}]"), token, "return=representation")
        return arr.getJSONObject(0).getString("id")
    }

    fun listPendingSessions(): JSONArray {
        val token = accessToken ?: return JSONArray()
        val local = deviceId ?: return JSONArray()
        return requestArray("/rest/v1/remote_sessions?target_device_id=eq.$local&status=eq.pending&select=*", "GET", null, token)
    }

    fun updateSession(sessionId: String, status: String) {
        val token = accessToken ?: return
        request("/rest/v1/remote_sessions?id=eq.$sessionId", "PATCH", JSONObject().put("status", status), token)
    }

    private fun request(path: String, method: String, body: Any, token: String? = accessToken, query: String? = null): JSONArray {
        val result = requestRaw(path, method, body.toString(), token, query)
        return JSONArray(result)
    }

    private fun requestArray(path: String, method: String, body: Any?, token: String): JSONArray =
        JSONArray(requestRaw(path, method, body?.toString(), token, null))

    private fun request(path: String, method: String, body: JSONObject): JSONObject =
        JSONObject(requestRaw(path, method, body.toString(), null, null))

    private fun request(path: String, method: String, body: JSONObject, token: String, query: String? = null): JSONObject =
        JSONObject(requestRaw(path, method, body.toString(), token, query))

    private fun requestRaw(path: String, method: String, body: String?, token: String?, query: String?): String {
        val url = URL(SUPABASE_URL + path + (if (query != null) "&$query" else ""))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("apikey", SUPABASE_KEY)
            setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) doOutput = true
        }
        body?.let { conn.outputStream.use { out -> out.write(it.toByteArray()) } }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
        if (conn.responseCode !in 200..299) throw IllegalStateException("Supabase ${conn.responseCode}: $text")
        return text
    }
}

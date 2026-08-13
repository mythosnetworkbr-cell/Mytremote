package com.mythosnetwork.mytremote.remote

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Local device/session state only.
 * Supabase has intentionally been removed from the application.
 */
class RemoteApi(private val context: Context) {
    private val prefs = context.getSharedPreferences("mythos_remote", Context.MODE_PRIVATE)

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
        prefs.edit()
            .putString("device_name", "Mythøs Remote")
            .putString("model", "${Build.MANUFACTURER} ${Build.MODEL}")
            .apply()
        return code
    }

    fun findDevice(code: String): JSONObject? {
        val localCode = deviceCode ?: registerDevice()
        if (code.trim().equals(localCode, ignoreCase = true)) {
            return JSONObject().apply {
                put("id", deviceId)
                put("device_code", localCode)
                put("device_name", "Mythøs Remote")
                put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("online", true)
            }
        }
        return null
    }

    fun createSession(targetDeviceId: String): String {
        return UUID.randomUUID().toString()
    }

    fun listPendingSessions(): JSONArray = JSONArray()

    fun updateSession(sessionId: String, status: String) {
        // Session signaling is intentionally handled outside the removed Supabase backend.
    }
}

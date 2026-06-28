package com.heartbeets.sharing

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * A heartbeat code — maps a local heartbeat ID to a shareable 10-char code.
 */
data class HeartbeatCode(
    val code: String,
    val heartbeatId: String,
    val name: String,
)

/**
 * Manages heartbeat codes locally (SharedPreferences).
 */
class CodeManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("heartbeat_codes", Context.MODE_PRIVATE)

    fun getCodes(): List<HeartbeatCode> {
        val json = prefs.getString("codes", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                HeartbeatCode(
                    code = obj.getString("code"),
                    heartbeatId = obj.getString("heartbeatId"),
                    name = obj.optString("name", ""),
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun addCode(code: HeartbeatCode) {
        val codes = getCodes().toMutableList()
        codes.add(code)
        save(codes)
    }

    fun removeCode(code: String) {
        val codes = getCodes().filter { it.code != code }
        save(codes)
    }

    fun generateCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no I, O, 0, 1 for clarity
        return (1..10).map { chars.random() }.joinToString("")
    }

    private fun save(codes: List<HeartbeatCode>) {
        val arr = JSONArray()
        codes.forEach { c ->
            arr.put(JSONObject().apply {
                put("code", c.code)
                put("heartbeatId", c.heartbeatId)
                put("name", c.name)
            })
        }
        prefs.edit().putString("codes", arr.toString()).apply()
    }
}

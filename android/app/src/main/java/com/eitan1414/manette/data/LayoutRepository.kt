package com.eitan1414.manette.data

import android.content.Context
import com.eitan1414.manette.model.ControlPlacement
import com.eitan1414.manette.model.DefaultLayout
import org.json.JSONArray
import org.json.JSONObject

class LayoutRepository(context: Context) {
    private val prefs = context.getSharedPreferences("manette", Context.MODE_PRIVATE)

    fun loadLayout(): List<ControlPlacement> {
        val saved = prefs.getString(KEY_LAYOUT, null) ?: return DefaultLayout.controls
        return runCatching {
            val array = JSONArray(saved)
            val overrides = mutableMapOf<String, Triple<Float, Float, Float>>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                overrides[item.getString("id")] = Triple(
                    item.getDouble("x").toFloat(),
                    item.getDouble("y").toFloat(),
                    item.getDouble("sizeDp").toFloat()
                )
            }
            DefaultLayout.controls.map { base ->
                val value = overrides[base.id]
                if (value == null) base else base.copy(
                    x = value.first.coerceIn(0.02f, 0.98f),
                    y = value.second.coerceIn(0.02f, 0.98f),
                    sizeDp = value.third.coerceIn(38f, 190f)
                )
            }
        }.getOrElse { DefaultLayout.controls }
    }

    fun saveLayout(layout: List<ControlPlacement>) {
        val array = JSONArray()
        layout.forEach { control ->
            array.put(JSONObject().apply {
                put("id", control.id)
                put("x", control.x)
                put("y", control.y)
                put("sizeDp", control.sizeDp)
            })
        }
        prefs.edit().putString(KEY_LAYOUT, array.toString()).apply()
    }

    fun resetLayout() {
        prefs.edit().remove(KEY_LAYOUT).apply()
    }

    fun loadIp(): String = prefs.getString(KEY_IP, "") ?: ""

    fun saveIp(ip: String) {
        prefs.edit().putString(KEY_IP, ip).apply()
    }

    fun loadChannel(): Int = prefs.getInt(KEY_CHANNEL, 0).coerceIn(0, 6)

    fun saveChannel(channel: Int) {
        prefs.edit().putInt(KEY_CHANNEL, channel.coerceIn(0, 6)).apply()
    }

    companion object {
        private const val KEY_LAYOUT = "controller_layout_v1"
        private const val KEY_IP = "wiiu_ip"
        private const val KEY_CHANNEL = "wiiu_pro_channel"
    }
}

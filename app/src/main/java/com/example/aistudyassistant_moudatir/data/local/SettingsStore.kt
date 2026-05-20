package com.example.aistudyassistant_moudatir.data.local

import android.content.Context

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("neya_settings", Context.MODE_PRIVATE)

    var userName: String
        get() = prefs.getString("user_name", "") ?: ""
        set(v) = prefs.edit().putString("user_name", v).apply()

    var serverUrl: String
        get() = prefs.getString("server_url", "http://10.0.2.2:8000") ?: "http://10.0.2.2:8000"
        set(v) = prefs.edit().putString("server_url", v).apply()

    // "auto" = détection automatique, sinon valeur exacte de la voix edge-tts
    var voice: String
        get() = prefs.getString("voice", "auto") ?: "auto"
        set(v) = prefs.edit().putString("voice", v).apply()
}

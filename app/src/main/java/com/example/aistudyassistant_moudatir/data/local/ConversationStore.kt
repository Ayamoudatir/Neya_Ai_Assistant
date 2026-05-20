package com.example.aistudyassistant_moudatir.data.local

import android.content.Context
import com.example.aistudyassistant_moudatir.data.model.Conversation
import com.example.aistudyassistant_moudatir.data.model.Message
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ConversationStore(context: Context) {

    private val prefs = context.getSharedPreferences("neya_store", Context.MODE_PRIVATE)
    private val gson  = Gson()

    // ── Conversations ─────────────────────────────────────────────────────────

    fun getConversations(): List<Conversation> {
        val json = prefs.getString("conversations", "[]") ?: "[]"
        val type = object : TypeToken<List<Conversation>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveConversation(conv: Conversation) {
        val list = getConversations().toMutableList()
        val idx  = list.indexOfFirst { it.id == conv.id }
        if (idx >= 0) list[idx] = conv else list.add(0, conv)
        // Garder max 30 conversations
        val trimmed = list.take(30)
        prefs.edit().putString("conversations", gson.toJson(trimmed)).apply()
    }

    fun deleteConversation(id: String) {
        val list = getConversations().filter { it.id != id }
        prefs.edit()
            .putString("conversations", gson.toJson(list))
            .remove("messages_$id")
            .apply()
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    fun getMessages(convId: String): List<Message> {
        val json = prefs.getString("messages_$convId", "[]") ?: "[]"
        val type = object : TypeToken<List<Message>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveMessages(convId: String, messages: List<Message>) {
        prefs.edit().putString("messages_$convId", gson.toJson(messages)).apply()
    }
}

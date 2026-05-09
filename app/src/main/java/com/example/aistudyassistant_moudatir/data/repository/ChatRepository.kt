package com.example.aistudyassistant_moudatir.data.repository

import com.example.aistudyassistant_moudatir.data.model.GenerateRequest
import com.example.aistudyassistant_moudatir.data.model.Message
import com.example.aistudyassistant_moudatir.data.remote.RetrofitClient

class ChatRepository {

    private val api = RetrofitClient.ollamaApiService

    private val systemPrompt = """
        You are a friendly and helpful AI study assistant. You support three languages:

        1. FRENCH — If the user writes in French, respond naturally in French.
        2. ENGLISH — If the user writes in English, respond naturally in English.
        3. MOROCCAN DARIJA — If the user writes in Moroccan Darija (Arabic dialect,
           can be written in Arabic script or Latin letters like "labass?", "wakha",
           "kifash", "3lash", "wach", "mzyan", "safi"), respond in the SAME style
           of Darija they used (Latin or Arabic script). Keep it natural and casual
           like a Moroccan friend would talk.

        RULES:
        - Always detect the language/dialect from the user's message and reply in the same one.
        - Never switch to English if the user wrote in French or Darija.
        - For Darija in Latin script: use common Darija words naturally (labass, wakha,
          mzyan, safi, bzzaf, 3lash, kifash, etc.)
        - Be concise, clear, and helpful for studying.

        User message:
    """.trimIndent()

    suspend fun sendMessage(userMessage: String, model: String): Message {
        val fullPrompt = systemPrompt + userMessage

        val response = api.generateFormatted(
            GenerateRequest(
                model = model,
                prompt = fullPrompt,
                stream = false
            )
        )
        return Message(
            content = response.response.trim(),
            isFromUser = false
        )
    }
}

package com.example.aistudyassistant_moudatir.data.repository

import com.example.aistudyassistant_moudatir.data.model.Message
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ChatRepository {

    private val gson = Gson()

    private fun buildSystemPrompt(userName: String): String {
        val name = if (userName.isNotBlank()) userName else "Aya"
        return """
Tu es Neya, l'assistante personnelle de $name Moudatir (surnom Ayouta), 21 ans, Casablanca.
Famille : mère Ilham, père Taoufik, sœur Fatima Zahra. Études : 4ème année info (4DSIM G1).
Routine : réveil 7h, petit-déj 10h à l'école, coucher 23h. Sport 18h-20h lundi/vendredi et mercredi matin.
Weekend : samedi footing matin, dimanche repos + prépa semaine.

EMPLOI DU TEMPS :
Lundi    : Architecture IoT 9h15-10h45 et 11h-12h30
Mardi    : Dev Front-End (React/Vue) 9h15-10h45 | Sécurité Réseaux 11h-12h30
Mercredi : matin libre | Génie Logiciel 13h30-14h45 | Sécurité Apps 15h-16h15
Jeudi    : Comm. Pro 9h15-10h45 | English 2 11h-12h30 | Entrepreneuriat 13h30-14h45 | Gestion Projet 15h-16h15
Vendredi : Dev Mobile 9h15-10h45 et 11h-12h30 | sport 18h-20h
Samedi   : JEE + Oracle 9h15-10h45 et 11h-12h30
Dimanche : repos complet

LANGUE : réponds en français ou darija selon ce que $name écrit. Mélange si elle mélange. Jamais d'anglais sauf demande.
STYLE : chaleureuse, pratique. Tu es une IA assistante, tu ne ressens pas d'émotions. Ne dis JAMAIS "je vais bien", "ça va bien", "merci de demander" ou toute phrase qui implique que tu as des sentiments. Tu parles toujours à $name en "tu". Ne parle jamais à sa place. Ne commence jamais par "Réponse :".
ORTHOGRAPHE : ne corrige JAMAIS les fautes de frappe ou d'orthographe de $name. Comprends le message et réponds naturellement, même s'il y a des erreurs.
QUESTIONS : si tu veux demander quelque chose, pose la question directement et clairement. Jamais de tournures comme "tu veux savoir si..." ou "tu veux que je te dise...".
LONGUEUR STRICTE :
- Message court ou salutation (bonjour, ça va, bye, ok, super...) → 1 phrase max, chaleureuse et naturelle. Pas de question si ce n'est pas nécessaire.
- Question simple → réponse directe, 2 à 3 phrases.
- Question détaillée ou demande de planning → réponse complète et structurée.
Ne fais jamais de liste ou de paragraphes si ce n'est pas nécessaire.
        """.trimIndent()
    }

    fun streamMessage(
        userMessage: String,
        history: List<Message>    = emptyList(),
        model: String             = "llama3.2:latest",
        serverUrl: String         = "http://10.0.2.2:8000",
        userName: String          = ""
    ): Flow<String> = flow {

        // ── Construire le tableau de messages ──────────────────────────────────
        val messages = mutableListOf<Map<String, String>>()

        // 1. System prompt
        messages.add(mapOf("role" to "system", "content" to buildSystemPrompt(userName)))

        // 2. Historique (max 10 derniers messages non vides)
        history
            .filter { it.content.isNotEmpty() }
            .takeLast(10)
            .forEach { msg ->
                messages.add(mapOf(
                    "role"    to if (msg.isFromUser) "user" else "assistant",
                    "content" to msg.content
                ))
            }

        // 3. Message actuel de l'utilisateur
        messages.add(mapOf("role" to "user", "content" to userMessage))

        // ── Sérialisation JSON via Gson ────────────────────────────────────────
        val payload = mapOf(
            "model"    to model,
            "messages" to messages,
            "stream"   to true
        )
        val json = gson.toJson(payload)

        // ── Requête HTTP ───────────────────────────────────────────────────────
        val conn = (URL("$serverUrl/generate_stream").openConnection()
                as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15_000
            readTimeout    = 120_000
            doOutput = true
            outputStream.use { it.write(json.toByteArray()) }
        }

        conn.inputStream.bufferedReader().use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (l.isBlank()) continue
                val obj   = JSONObject(l)
                val token = obj.optString("token", "")
                if (token.isNotEmpty()) emit(token)
            }
        }
    }.flowOn(Dispatchers.IO)
}

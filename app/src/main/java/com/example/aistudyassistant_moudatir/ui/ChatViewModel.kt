package com.example.aistudyassistant_moudatir.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.aistudyassistant_moudatir.data.local.SettingsStore
import com.example.aistudyassistant_moudatir.data.model.Message
import com.example.aistudyassistant_moudatir.data.repository.ChatRepository
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settings   = SettingsStore(application)
    private val repository = ChatRepository()

    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    var currentConversationId: String = UUID.randomUUID().toString()
        private set

    val currentConversationTitle: String
        get() = _messages.value
            ?.firstOrNull { it.isFromUser }
            ?.content
            ?.take(40)
            ?: "Nouvelle discussion"

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        appendMessage(Message(content = content, isFromUser = true))
        _isLoading.value = true
        _error.value = null

        val placeholder = Message(content = "", isFromUser = false)
        appendMessage(placeholder)

        // Historique = tous les messages non vides SAUF le message actuel (déjà dans userMessage)
        val history = _messages.value.orEmpty()
            .filter { it.content.isNotEmpty() }
            .dropLast(1)  // retire le message actuel qu'on vient d'ajouter

        viewModelScope.launch {
            try {
                repository.streamMessage(
                    userMessage = content,
                    history     = history,
                    model       = "llama3.2:latest",
                    serverUrl   = settings.serverUrl,
                    userName    = settings.userName
                ).collect { token ->
                    val current = _messages.value.orEmpty().toMutableList()
                    val idx = current.indexOfLast { it.id == placeholder.id }
                    if (idx >= 0) {
                        current[idx] = current[idx].copy(content = current[idx].content + token)
                        _messages.value = current
                    }
                }
            } catch (e: Exception) {
                _error.value = "Erreur : impossible de joindre le serveur."
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Ajoute un message image dans le chat (sans déclencher la réponse IA). */
    fun sendImageMessage(imageUri: String) {
        appendMessage(Message(content = "", isFromUser = true, imageUri = imageUri))
    }

    fun startNewConversation() {
        currentConversationId = UUID.randomUUID().toString()
        _messages.value = emptyList()
        _error.value = null
        _isLoading.value = false
    }

    fun loadConversation(id: String, messages: List<Message>) {
        currentConversationId = id
        _messages.value = messages
        _error.value = null
    }

    private fun appendMessage(message: Message) {
        val updated = _messages.value.orEmpty().toMutableList()
        updated.add(message)
        _messages.value = updated
    }
}

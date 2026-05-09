package com.example.aistudyassistant_moudatir.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aistudyassistant_moudatir.data.model.Message
import com.example.aistudyassistant_moudatir.data.repository.ChatRepository
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val repository = ChatRepository()

    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val model = "llama3.2:3b"

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        val userMessage = Message(content = content, isFromUser = true)
        appendMessage(userMessage)
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val response = repository.sendMessage(content, model)
                appendMessage(response)
            } catch (e: Exception) {
                _error.value = "Erreur : impossible de joindre le serveur.\nVérifie que FastAPI tourne sur le Mac."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun appendMessage(message: Message) {
        val updated = _messages.value.orEmpty().toMutableList()
        updated.add(message)
        _messages.value = updated
    }
}

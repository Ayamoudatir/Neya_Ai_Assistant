package com.example.aistudyassistant_moudatir.data.model

data class Message(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val isFromUser: Boolean,
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

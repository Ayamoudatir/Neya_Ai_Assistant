package com.example.aistudyassistant_moudatir.data.model

data class Conversation(
    val id: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

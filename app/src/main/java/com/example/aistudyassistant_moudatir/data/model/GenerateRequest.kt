package com.example.aistudyassistant_moudatir.data.model

data class GenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false
)

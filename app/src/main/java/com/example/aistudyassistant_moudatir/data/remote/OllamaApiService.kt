package com.example.aistudyassistant_moudatir.data.remote

import com.example.aistudyassistant_moudatir.data.model.GenerateRequest
import com.example.aistudyassistant_moudatir.data.model.GenerateResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface OllamaApiService {

    @POST("generate_formatted")
    suspend fun generateFormatted(@Body request: GenerateRequest): GenerateResponse
}

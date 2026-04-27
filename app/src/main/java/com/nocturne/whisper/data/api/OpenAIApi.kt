package com.nocturne.whisper.data.api

import com.nocturne.whisper.data.model.ChatRequest
import com.nocturne.whisper.data.model.ChatResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

interface OpenAIApi {

    @POST
    suspend fun chatCompletion(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: ChatRequest
    ): Response<ChatResponse>

    @POST
    @Streaming
    suspend fun chatCompletionStream(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("Accept") accept: String = "text/event-stream",
        @Body request: ChatRequest
    ): Response<ResponseBody>
}

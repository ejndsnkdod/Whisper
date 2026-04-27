package com.nocturne.whisper.data.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.nocturne.whisper.data.model.ChatMessageContentAdapter
import com.nocturne.whisper.data.model.MessageContent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class RetrofitClient {

    private var openAIApi: OpenAIApi? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val gson: Gson = GsonBuilder()
        .serializeNulls()
        .setLenient()
        .registerTypeAdapter(MessageContent::class.java, ChatMessageContentAdapter())
        .create()

    private val kimiUserAgentInterceptor = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        val next = if (url.contains("api.kimi.com")) {
            request.newBuilder()
                .header("User-Agent", "RooCode/1.0.0")
                .build()
        } else {
            request
        }
        chain.proceed(next)
    }

    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(kimiUserAgentInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun getApi(): OpenAIApi {
        if (openAIApi == null) {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.openai.com/")
                .client(createOkHttpClient())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
            openAIApi = retrofit.create(OpenAIApi::class.java)
        }
        return openAIApi!!
    }

    fun resetApi() {
        openAIApi = null
    }

    companion object {
        @Volatile
        private var INSTANCE: RetrofitClient? = null

        fun getInstance(): RetrofitClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RetrofitClient().also { INSTANCE = it }
            }
        }
    }
}

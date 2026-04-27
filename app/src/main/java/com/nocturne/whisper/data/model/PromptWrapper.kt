package com.nocturne.whisper.data.model

import com.google.gson.annotations.SerializedName

data class PromptWrapper(
    @SerializedName("data")
    val data: PersonaInfo? = null
)

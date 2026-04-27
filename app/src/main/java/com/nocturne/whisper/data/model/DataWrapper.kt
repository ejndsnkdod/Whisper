package com.nocturne.whisper.data.model

import com.google.gson.annotations.SerializedName

data class DataWrapper(
    @SerializedName("prompts")
    val prompts: Map<String, PromptWrapper> = emptyMap()
)

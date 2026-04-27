package com.nocturne.whisper.data.model

import com.google.gson.annotations.SerializedName

data class SaveFileData(
    @SerializedName("data")
    val data: DataWrapper? = null
)

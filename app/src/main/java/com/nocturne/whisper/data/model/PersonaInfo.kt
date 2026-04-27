package com.nocturne.whisper.data.model

import com.google.gson.annotations.SerializedName

data class PersonaInfo(
    @SerializedName("name")
    val name: String = "",

    @SerializedName("description")
    val description: String = "",

    @SerializedName("personality")
    val personality: String = "",

    @SerializedName("scenario")
    val scenario: String = "",

    @SerializedName("creator_notes")
    val creatorNotes: String = ""
)

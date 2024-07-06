package app.pachli.core.network.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HashTag(
    val name: String,
    val url: String,
    val history: List<HashTagHistory> = emptyList(),
    val following: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class HashTagHistory(
    val day: String,
    val accounts: Int,
    val uses: Int,
)

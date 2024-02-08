package app.pachli.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FilterResult(
    val filter: Filter,
    @Json(name = "keyword_matches") val keywordMatches: List<String>?,
    @Json(name = "status_matches") val statusMatches: List<String>?,
)

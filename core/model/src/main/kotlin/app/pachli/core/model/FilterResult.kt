package app.pachli.core.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FilterResult(
    val filter: ContentFilter,
    @Json(name = "keyword_matches") val keywordMatches: List<String>?,
    @Json(name = "status_matches") val statusMatches: List<String>?,
)

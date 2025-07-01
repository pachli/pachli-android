package app.pachli.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FilterKeyword(
    val id: String,
    val keyword: String,
    @Json(name = "whole_word") val wholeWord: Boolean,
) {
    fun asModel() = app.pachli.core.model.FilterKeyword(
        id = id,
        keyword = keyword,
        wholeWord = wholeWord,
    )
}

fun Iterable<FilterKeyword>.asModel() = map { it.asModel() }

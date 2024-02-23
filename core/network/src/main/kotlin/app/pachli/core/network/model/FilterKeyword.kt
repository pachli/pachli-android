package app.pachli.core.network.model

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonClass(generateAdapter = true)
data class FilterKeyword(
    val id: String,
    val keyword: String,
    @Json(name = "whole_word") val wholeWord: Boolean,
) : Parcelable

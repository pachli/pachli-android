package app.pachli.core.network.model

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonClass(generateAdapter = true)
data class Filter(
    val id: String = "",
    val title: String = "",
    @Json(name = "context") val contexts: Set<FilterContext> = emptySet(),
    @Json(name = "expires_at") val expiresAt: Date? = null,
    @Json(name = "filter_action") val filterAction: FilterAction = FilterAction.WARN,
    // This should not normally be empty. However, Mastodon does not include
    // this in a status' `filtered.filter` property (it's not null or empty,
    // it's missing) which breaks deserialisation. Patch this by ensuring it's
    // always initialised to an empty list.
    // TODO: https://github.com/mastodon/mastodon/issues/29142
    val keywords: List<FilterKeyword> = emptyList(),
    // val statuses: List<FilterStatus>,
) : Parcelable

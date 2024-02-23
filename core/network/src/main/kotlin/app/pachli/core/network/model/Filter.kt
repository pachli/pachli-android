package app.pachli.core.network.model

import android.os.Parcelable
import app.pachli.core.network.json.Default
import app.pachli.core.network.json.HasDefault
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonClass(generateAdapter = true)
data class Filter(
    val id: String,
    val title: String,
    val context: List<String>,
    @Json(name = "expires_at") val expiresAt: Date?,
    @Json(name = "filter_action") val filterAction: String,
    // This should not normally be empty. However, Mastodon does not include
    // this in a status' `filtered.filter` property (it's not null or empty,
    // it's missing) which breaks deserialisation. Patch this by ensuring it's
    // always initialised to an empty list.
    // TODO: https://github.com/mastodon/mastodon/issues/29142
    val keywords: List<FilterKeyword> = emptyList(),
    // val statuses: List<FilterStatus>,
) : Parcelable {
    @HasDefault
    enum class Action(val action: String) {
        NONE("none"),

        @Default
        WARN("warn"),
        HIDE("hide"),
        ;

        companion object {
            fun from(action: String): Action = entries.firstOrNull { it.action == action } ?: WARN
        }
    }

    @HasDefault
    enum class Kind(val kind: String) {
        HOME("home"),
        NOTIFICATIONS("notifications"),

        @Default
        PUBLIC("public"),
        THREAD("thread"),
        ACCOUNT("account"),
        ;

        companion object {
            fun from(kind: String): Kind = entries.firstOrNull { it.kind == kind } ?: PUBLIC

            fun from(kind: TimelineKind): Kind = when (kind) {
                is TimelineKind.Home, is TimelineKind.UserList -> HOME
                is TimelineKind.PublicFederated,
                is TimelineKind.PublicLocal,
                is TimelineKind.Tag,
                is TimelineKind.Favourites,
                -> PUBLIC
                is TimelineKind.User -> ACCOUNT
                else -> PUBLIC
            }
        }
    }

    val action: Action
        get() = Action.from(filterAction)

    val kinds: List<Kind>
        get() = context.map { Kind.from(it) }
}

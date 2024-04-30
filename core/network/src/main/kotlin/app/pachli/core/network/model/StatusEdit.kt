package app.pachli.core.network.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

/**
 * A snapshot of a status from its edit history
 *
 * See [StatusEdit](https://docs.joinmastodon.org/entities/StatusEdit/)
 */
@JsonClass(generateAdapter = true)
data class StatusEdit(
    val content: String,
    @Json(name = "spoiler_text") val spoilerText: String,
    val sensitive: Boolean,
    @Json(name = "created_at") val createdAt: Date,
    val account: TimelineAccount,
    val poll: PollEdit?,
    @Json(name = "media_attachments") val mediaAttachments: List<Attachment>,
    val emojis: List<Emoji>,
)

/**
 * A snapshot of a poll from a status' edit history.
 */
@JsonClass(generateAdapter = true)
data class PollEdit(
    val options: List<PollOptionEdit>,
)

/**
 * A snapshot of a poll option from a status' edit history.
 */
@JsonClass(generateAdapter = true)
data class PollOptionEdit(
    val title: String,
)

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
) {
    fun asModel() = app.pachli.core.model.StatusEdit(
        content = content,
        spoilerText = spoilerText,
        sensitive = sensitive,
        createdAt = createdAt,
        account = account.asModel(),
        poll = poll?.asModel(),
        mediaAttachments = mediaAttachments.asModel(),
        emojis = emojis.asModel(),
    )
}

@JvmName("iterableStatusEditAsModel")
fun Iterable<StatusEdit>.asModel() = map { it.asModel() }

/**
 * A snapshot of a poll from a status' edit history.
 */
@JsonClass(generateAdapter = true)
data class PollEdit(
    val options: List<PollOptionEdit>,
) {
    fun asModel() = app.pachli.core.model.PollEdit(
        options = options.asModel(),
    )
}

/**
 * A snapshot of a poll option from a status' edit history.
 */
@JsonClass(generateAdapter = true)
data class PollOptionEdit(
    val title: String,
) {
    fun asModel() = app.pachli.core.model.PollOptionEdit(
        title = title,
    )
}

@JvmName("iterablePollOptionEditAsModel")
fun Iterable<PollOptionEdit>.asModel() = map { it.asModel() }

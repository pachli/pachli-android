package app.pachli.core.model

import java.util.Date

/**
 * A snapshot of a status from its edit history
 *
 * See [StatusEdit](https://docs.joinmastodon.org/entities/StatusEdit/)
 */
data class StatusEdit(
    val content: String,
    val spoilerText: String,
    val sensitive: Boolean,
    val createdAt: Date,
    val account: TimelineAccount,
    val poll: PollEdit?,
    val mediaAttachments: List<Attachment>,
    val emojis: List<Emoji>,
)

/**
 * A snapshot of a poll from a status' edit history.
 */
data class PollEdit(
    val options: List<PollOptionEdit>,
)

/**
 * A snapshot of a poll option from a status' edit history.
 */
data class PollOptionEdit(
    val title: String,
)

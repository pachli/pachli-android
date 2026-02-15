package app.pachli.core.network.model

import app.pachli.core.network.json.BooleanIfNull
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Date

@JsonClass(generateAdapter = true)
data class Poll(
    val id: String,
    @Json(name = "expires_at") val expiresAt: Date?,
    val expired: Boolean,
    val multiple: Boolean,
    @Json(name = "votes_count") val votesCount: Int,
    @Json(name = "voters_count") val votersCount: Int?,
    val options: List<PollOption>,
    // Friendica can incorrectly return null for `voted`. Default to false.
    // https://github.com/friendica/friendica/issues/13922
    @BooleanIfNull(false) val voted: Boolean = false,
    @Json(name = "own_votes") val ownVotes: List<Int>?,
) {
    fun asModel() = app.pachli.core.model.Poll(
        id = id,
        expiresAt = expiresAt,
        expired = expired,
        multiple = multiple,
        votesCount = votesCount,
        votersCount = votersCount,
        options = options.asModel(),
        voted = voted,
        ownVotes = ownVotes,
    )
}

@JsonClass(generateAdapter = true)
data class PollOption(
    val title: String,
    @Json(name = "votes_count") val votesCount: Int?,
) {
    fun asModel() = app.pachli.core.model.PollOption(
        title = title,
        votesCount = votesCount ?: 0,
    )
}

fun Iterable<PollOption>.asModel() = map { it.asModel() }

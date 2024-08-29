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

    fun votedCopy(choices: List<Int>): Poll {
        val newOptions = options.mapIndexed { index, option ->
            if (choices.contains(index)) {
                option.copy(votesCount = option.votesCount + 1)
            } else {
                option
            }
        }

        return copy(
            options = newOptions,
            votesCount = votesCount + choices.size,
            votersCount = votersCount?.plus(1),
            voted = true,
        )
    }

    fun toNewPoll(creationDate: Date) = NewPoll(
        options.map { it.title },
        expiresAt?.let {
            ((it.time - creationDate.time) / 1000).toInt() + 1
        } ?: 3600,
        multiple,
    )
}

@JsonClass(generateAdapter = true)
data class PollOption(
    val title: String,
    @Json(name = "votes_count") val votesCount: Int,
)

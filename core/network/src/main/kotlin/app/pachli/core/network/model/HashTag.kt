package app.pachli.core.network.model

import com.squareup.moshi.JsonClass

/**
 * @param name Hashtag name, without the leading `#`.
 */
@JsonClass(generateAdapter = true)
data class HashTag(
    val name: String,
    val url: String,
    val history: List<HashTagHistory> = emptyList(),
    val following: Boolean? = null,
) {
    fun asModel() = app.pachli.core.model.HashTag(
        name = name,
        url = url,
        history = history.asModel(),
        following = following,
    )
}

@JvmName("iterableHashTagAsModel")
fun Iterable<HashTag>.asModel() = map { it.asModel() }

@JsonClass(generateAdapter = true)
data class HashTagHistory(
    val day: String,
    val accounts: Int,
    val uses: Int,
) {
    fun asModel() = app.pachli.core.model.HashTagHistory(
        day = day,
        accounts = accounts,
        uses = uses,
    )
}

@JvmName("iterableHashTagHistoryAsModel")
fun Iterable<HashTagHistory>.asModel() = map { it.asModel() }

/* Copyright 2017 Andrew Dawson
 *
 * This file is a part of Pachli.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Pachli is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Pachli; if not,
 * see <http://www.gnu.org/licenses>.
 */

package app.pachli.core.network.model

import app.pachli.core.model.MastodonList
import app.pachli.core.network.json.Default
import app.pachli.core.network.json.HasDefault
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** https://docs.joinmastodon.org/entities/List/#replies_policy */
@HasDefault
enum class UserListRepliesPolicy {
    /** Show replies to any followed user */
    @Json(name = "followed")
    FOLLOWED,

    /** Show replies to members of the list */
    @Default
    @Json(name = "list")
    LIST,

    /** Show replies to no one */
    @Json(name = "none")
    NONE,

    ;

    fun asModel() = when (this) {
        FOLLOWED -> app.pachli.core.model.UserListRepliesPolicy.FOLLOWED
        LIST -> app.pachli.core.model.UserListRepliesPolicy.LIST
        NONE -> app.pachli.core.model.UserListRepliesPolicy.NONE
    }

    // Empty companion object so that other code can add extension functions
    // to this enum. See e.g., ListsActivity.
    companion object
}

fun app.pachli.core.model.UserListRepliesPolicy.asNetworkModel() = when (this) {
    app.pachli.core.model.UserListRepliesPolicy.FOLLOWED -> UserListRepliesPolicy.FOLLOWED
    app.pachli.core.model.UserListRepliesPolicy.LIST -> UserListRepliesPolicy.LIST
    app.pachli.core.model.UserListRepliesPolicy.NONE -> UserListRepliesPolicy.NONE
}

@JsonClass(generateAdapter = true)
data class MastoList(
    val id: String,
    val title: String,
    /**
     * List's exclusivity (whether posts are hidden from the home timeline).
     * Null implies the server does not support this feature.
     */
    val exclusive: Boolean? = null,

    @Json(name = "replies_policy")
    val repliesPolicy: UserListRepliesPolicy = UserListRepliesPolicy.LIST,
) {
    fun asModel() = MastodonList(
        listId = id,
        title = title,
        repliesPolicy = repliesPolicy.asModel(),
        exclusive = exclusive == true,
    )
}

fun Iterable<MastoList>.asModel() = map { it.asModel() }

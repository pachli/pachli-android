/*
 * Copyright (c) 2026 Pachli Association
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

package app.pachli.core.data.repository.hashtags

import app.pachli.core.common.PachliError
import app.pachli.core.model.Hashtag
import app.pachli.core.network.retrofit.apiresult.ApiError
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow

interface HashtagsError : PachliError {
    @JvmInline
    value class Retrieve(private val error: ApiError) : HashtagsError, PachliError by error

    @JvmInline
    value class Follow(private val error: ApiError) : HashtagsError, PachliError by error

    @JvmInline
    value class Unfollow(private val error: ApiError) : HashtagsError, PachliError by error
}

interface HashtagsRepository {
    /** @return Known followed hashtags for [pachliAccountId]. */
    fun getFollowedHashtags(pachliAccountId: Long): Flow<List<Hashtag>>

    /**
     * Refresh followed hashtags for [pachliAccountId].
     *
     * @return Latest followed hashtags, or an error.
     */
    suspend fun refreshFollowedHashtags(pachliAccountId: Long): Result<List<Hashtag>, HashtagsError.Retrieve>

    /**
     * Follow [hashtag].
     *
     * @return The server's latest view of [hashtag], or an error.
     */
    suspend fun followHashtag(pachliAccountId: Long, hashtag: String): Result<Hashtag, HashtagsError.Follow>

    /**
     * Unfollow [hashtag].
     *
     * @return The server's latest view of [hashtag], or an error.
     */
    suspend fun unfollowHashtag(pachliAccountId: Long, hashtag: String): Result<Hashtag, HashtagsError.Unfollow>
}

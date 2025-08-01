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

package app.pachli.core.network.retrofit

import app.pachli.core.model.NewContentFilter
import app.pachli.core.network.model.AccessToken
import app.pachli.core.network.model.Account
import app.pachli.core.network.model.Announcement
import app.pachli.core.network.model.AppCredentials
import app.pachli.core.network.model.Attachment
import app.pachli.core.network.model.Conversation
import app.pachli.core.network.model.CredentialAccount
import app.pachli.core.network.model.DeletedStatus
import app.pachli.core.network.model.Emoji
import app.pachli.core.network.model.Filter
import app.pachli.core.network.model.FilterAction
import app.pachli.core.network.model.FilterContext
import app.pachli.core.network.model.FilterKeyword
import app.pachli.core.network.model.FilterV1
import app.pachli.core.network.model.HashTag
import app.pachli.core.network.model.InstanceV1
import app.pachli.core.network.model.InstanceV2
import app.pachli.core.network.model.Marker
import app.pachli.core.network.model.MastoList
import app.pachli.core.network.model.MediaUploadResult
import app.pachli.core.network.model.NewStatus
import app.pachli.core.network.model.Notification
import app.pachli.core.network.model.NotificationSubscribeResult
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.Relationship
import app.pachli.core.network.model.ScheduledStatus
import app.pachli.core.network.model.SearchResult
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.StatusContext
import app.pachli.core.network.model.StatusEdit
import app.pachli.core.network.model.StatusSource
import app.pachli.core.network.model.Suggestion
import app.pachli.core.network.model.TimelineAccount
import app.pachli.core.network.model.Translation
import app.pachli.core.network.model.TrendingTag
import app.pachli.core.network.model.TrendsLink
import app.pachli.core.network.model.UserListRepliesPolicy
import app.pachli.core.network.retrofit.apiresult.ApiResult
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * for documentation of the Mastodon REST API see https://docs.joinmastodon.org/api/
 */

@JvmSuppressWildcards
interface MastodonApi {

    companion object {
        const val ENDPOINT_AUTHORIZE = "oauth/authorize"
        const val DOMAIN_HEADER = "domain"
        const val PLACEHOLDER_DOMAIN = "dummy.placeholder"
    }

    @GET("/api/v1/custom_emojis")
    suspend fun getCustomEmojis(): ApiResult<List<Emoji>>

    @GET("api/v1/instance")
    suspend fun getInstanceV1(@Header(DOMAIN_HEADER) domain: String? = null): ApiResult<InstanceV1>

    @GET("api/v2/instance")
    suspend fun getInstanceV2(@Header(DOMAIN_HEADER) domain: String? = null): ApiResult<InstanceV2>

    @GET("api/v1/filters")
    suspend fun getContentFiltersV1(): ApiResult<List<FilterV1>>

    @GET("api/v2/filters")
    suspend fun getContentFilters(): ApiResult<List<Filter>>

    @GET("api/v2/filters/{id}")
    suspend fun getFilter(
        @Path("id") filterId: String,
    ): ApiResult<Filter>

    @GET("api/v1/filters/{id}")
    suspend fun getFilterV1(
        @Path("id") filterId: String,
    ): ApiResult<FilterV1>

    @GET("api/v1/timelines/home")
    suspend fun homeTimeline(
        @Query("max_id") maxId: String? = null,
        @Query("min_id") minId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("limit") limit: Int? = null,
    ): ApiResult<List<Status>>

    @GET("api/v1/timelines/public")
    suspend fun publicTimeline(
        @Query("local") local: Boolean? = null,
        @Query("max_id") maxId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("min_id") minId: String? = null,
        @Query("limit") limit: Int? = null,
    ): ApiResult<List<Status>>

    @GET("api/v1/timelines/tag/{hashtag}")
    suspend fun hashtagTimeline(
        @Path("hashtag") hashtag: String,
        @Query("any[]") any: List<String>?,
        @Query("local") local: Boolean?,
        @Query("max_id") maxId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("min_id") minId: String? = null,
        @Query("limit") limit: Int? = null,
    ): ApiResult<List<Status>>

    @GET("api/v1/timelines/list/{listId}")
    suspend fun listTimeline(
        @Path("listId") listId: String,
        @Query("max_id") maxId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("min_id") minId: String? = null,
        @Query("limit") limit: Int? = null,
    ): ApiResult<List<Status>>

    @GET("api/v1/timelines/link")
    suspend fun linkTimeline(
        @Query("url") url: String,
        @Query("max_id") maxId: String? = null,
        @Query("min_id") minId: String? = null,
        @Query("limit") limit: Int? = null,
    ): ApiResult<List<Status>>

    @GET("api/v1/notifications")
    suspend fun notifications(
        /** Return results older than this ID */
        @Query("max_id") maxId: String? = null,
        /** Return results immediately newer than this ID */
        @Query("min_id") minId: String? = null,
        /** Maximum number of results to return. Defaults to 15, max is 30 */
        @Query("limit") limit: Int? = null,
        /** Types to excludes from the results */
        @Query("exclude_types[]") excludes: Iterable<Notification.Type>? = null,
        /** Include notifications filtered by the user's notifications filter policy. */
        @Query("include_filtered") includeFiltered: Boolean = true,
    ): ApiResult<List<Notification>>

    /** Fetch a single notification */
    @GET("api/v1/notifications/{id}")
    suspend fun notification(
        @Path("id") id: String,
    ): ApiResult<Notification>

    @GET("api/v1/markers")
    suspend fun markersWithAuth(
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String,
        @Query("timeline[]") timelines: List<String>,
    ): ApiResult<Map<String, Marker>>

    @FormUrlEncoded
    @POST("api/v1/markers")
    suspend fun updateMarkersWithAuth(
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String,
        @Field("home[last_read_id]") homeLastReadId: String? = null,
        @Field("notifications[last_read_id]") notificationsLastReadId: String? = null,
    ): ApiResult<Unit>

    @GET("api/v1/notifications")
    suspend fun notificationsWithAuth(
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String,
        /** Return results immediately newer than this ID */
        @Query("min_id") minId: String?,
    ): ApiResult<List<Notification>>

    @POST("api/v1/notifications/clear")
    suspend fun clearNotifications(): ApiResult<Unit>

    @FormUrlEncoded
    @PUT("api/v1/media/{mediaId}")
    suspend fun updateMedia(
        @Path("mediaId") mediaId: String,
        @Field("description") description: String? = null,
        @Field("focus") focus: String? = null,
    ): ApiResult<Attachment>

    @GET("api/v1/media/{mediaId}")
    suspend fun getMedia(
        @Path("mediaId") mediaId: String,
    ): ApiResult<MediaUploadResult>

    @POST("api/v1/statuses")
    suspend fun createStatus(
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body status: NewStatus,
    ): ApiResult<Status>

    @POST("api/v1/statuses")
    suspend fun createScheduledStatus(
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body status: NewStatus,
    ): ApiResult<ScheduledStatus>

    @GET("api/v1/statuses/{id}")
    suspend fun status(
        @Path("id") statusId: String,
    ): ApiResult<Status>

    @GET("api/v1/statuses")
    suspend fun statuses(
        @Query("id[]") ids: List<String>,
    ): ApiResult<List<Status>>

    @PUT("api/v1/statuses/{id}")
    suspend fun editStatus(
        @Path("id") statusId: String,
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body editedStatus: NewStatus,
    ): ApiResult<Status>

    @GET("api/v1/statuses/{id}/source")
    suspend fun statusSource(
        @Path("id") statusId: String,
    ): ApiResult<StatusSource>

    @GET("api/v1/statuses/{id}/context")
    suspend fun statusContext(
        @Path("id") statusId: String,
    ): ApiResult<StatusContext>

    @GET("api/v1/statuses/{id}/history")
    suspend fun statusEdits(
        @Path("id") statusId: String,
    ): ApiResult<List<StatusEdit>>

    @GET("api/v1/statuses/{id}/reblogged_by")
    suspend fun statusRebloggedBy(
        @Path("id") statusId: String,
        @Query("max_id") maxId: String?,
    ): ApiResult<List<TimelineAccount>>

    @GET("api/v1/statuses/{id}/favourited_by")
    suspend fun statusFavouritedBy(
        @Path("id") statusId: String,
        @Query("max_id") maxId: String?,
    ): ApiResult<List<TimelineAccount>>

    @DELETE("api/v1/statuses/{id}")
    suspend fun deleteStatus(
        @Path("id") statusId: String,
    ): ApiResult<DeletedStatus>

    @POST("api/v1/statuses/{id}/reblog")
    suspend fun reblogStatus(
        @Path("id") statusId: String,
    ): ApiResult<Status>

    @POST("api/v1/statuses/{id}/unreblog")
    suspend fun unreblogStatus(
        @Path("id") statusId: String,
    ): ApiResult<Status>

    @POST("api/v1/statuses/{id}/favourite")
    suspend fun favouriteStatus(
        @Path("id") statusId: String,
    ): ApiResult<Status>

    @POST("api/v1/statuses/{id}/unfavourite")
    suspend fun unfavouriteStatus(
        @Path("id") statusId: String,
    ): ApiResult<Status>

    @POST("api/v1/statuses/{id}/bookmark")
    suspend fun bookmarkStatus(
        @Path("id") statusId: String,
    ): ApiResult<Status>

    @POST("api/v1/statuses/{id}/unbookmark")
    suspend fun unbookmarkStatus(
        @Path("id") statusId: String,
    ): ApiResult<Status>

    @POST("api/v1/statuses/{id}/pin")
    suspend fun pinStatus(
        @Path("id") statusId: String,
    ): ApiResult<Status>

    @POST("api/v1/statuses/{id}/unpin")
    suspend fun unpinStatus(
        @Path("id") statusId: String,
    ): ApiResult<Status>

    @POST("api/v1/statuses/{id}/mute")
    suspend fun muteConversation(
        @Path("id") statusId: String,
    ): ApiResult<Status>

    @POST("api/v1/statuses/{id}/unmute")
    suspend fun unmuteConversation(
        @Path("id") statusId: String,
    ): ApiResult<Status>

    @POST("api/v1/statuses/{id}/translate")
    suspend fun translate(
        @Path("id") statusId: String,
    ): ApiResult<Translation>

    @GET("api/v1/scheduled_statuses")
    suspend fun scheduledStatuses(
        @Query("limit") limit: Int? = null,
        @Query("max_id") maxId: String? = null,
    ): ApiResult<List<ScheduledStatus>>

    @DELETE("api/v1/scheduled_statuses/{id}")
    suspend fun deleteScheduledStatus(
        @Path("id") scheduledStatusId: String,
    ): ApiResult<Unit>

    @GET("api/v1/accounts/verify_credentials")
    suspend fun accountVerifyCredentials(
        @Header(DOMAIN_HEADER) domain: String? = null,
        @Header("Authorization") auth: String? = null,
    ): ApiResult<CredentialAccount>

    @FormUrlEncoded
    @PATCH("api/v1/accounts/update_credentials")
    suspend fun accountUpdateSource(
        @Field("source[privacy]") privacy: String?,
        @Field("source[sensitive]") sensitive: Boolean?,
        @Field("source[language]") language: String?,
    ): ApiResult<CredentialAccount>

    @Multipart
    @PATCH("api/v1/accounts/update_credentials")
    suspend fun accountUpdateCredentials(
        @Part(value = "display_name") displayName: RequestBody?,
        @Part(value = "note") note: RequestBody?,
        @Part(value = "locked") locked: RequestBody?,
        @Part avatar: MultipartBody.Part?,
        @Part header: MultipartBody.Part?,
        @Part(value = "fields_attributes[0][name]") fieldName0: RequestBody?,
        @Part(value = "fields_attributes[0][value]") fieldValue0: RequestBody?,
        @Part(value = "fields_attributes[1][name]") fieldName1: RequestBody?,
        @Part(value = "fields_attributes[1][value]") fieldValue1: RequestBody?,
        @Part(value = "fields_attributes[2][name]") fieldName2: RequestBody?,
        @Part(value = "fields_attributes[2][value]") fieldValue2: RequestBody?,
        @Part(value = "fields_attributes[3][name]") fieldName3: RequestBody?,
        @Part(value = "fields_attributes[3][value]") fieldValue3: RequestBody?,
    ): ApiResult<Account>

    @GET("api/v1/accounts/search")
    suspend fun searchAccounts(
        @Query("q") query: String,
        @Query("resolve") resolve: Boolean? = null,
        @Query("limit") limit: Int? = null,
        @Query("following") following: Boolean? = null,
    ): ApiResult<List<TimelineAccount>>

    @GET("api/v1/accounts/{id}")
    suspend fun account(
        @Path("id") accountId: String,
    ): ApiResult<Account>

    /**
     * Method to fetch statuses for the specified account.
     * @param accountId ID for account for which statuses will be requested
     * @param maxId Only statuses with ID less than maxId will be returned
     * @param sinceId Only statuses with ID bigger than sinceId will be returned
     * @param minId Only statuses with ID greater than minId will be returned
     * @param limit Limit returned statuses
     * @param excludeReblogs only return statuses that are not reblogs
     * @param excludeReplies only return statuses that are no replies
     * @param onlyMedia only return statuses that have media attached
     */
    @GET("api/v1/accounts/{id}/statuses")
    suspend fun accountStatuses(
        @Path("id") accountId: String,
        @Query("max_id") maxId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("min_id") minId: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("exclude_reblogs") excludeReblogs: Boolean? = null,
        @Query("exclude_replies") excludeReplies: Boolean? = null,
        @Query("only_media") onlyMedia: Boolean? = null,
        @Query("pinned") pinned: Boolean? = null,
    ): ApiResult<List<Status>>

    @GET("api/v1/accounts/{id}/followers")
    suspend fun accountFollowers(
        @Path("id") accountId: String,
        @Query("max_id") maxId: String?,
    ): ApiResult<List<TimelineAccount>>

    @GET("api/v1/accounts/{id}/following")
    suspend fun accountFollowing(
        @Path("id") accountId: String,
        @Query("max_id") maxId: String? = null,
        @Query("limit") limit: Int = 80,
    ): ApiResult<List<TimelineAccount>>

    @FormUrlEncoded
    @POST("api/v1/accounts/{id}/follow")
    suspend fun followAccount(
        @Path("id") accountId: String,
        @Field("reblogs") showReblogs: Boolean? = null,
        @Field("notify") notify: Boolean? = null,
    ): ApiResult<Relationship>

    @POST("api/v1/accounts/{id}/unfollow")
    suspend fun unfollowAccount(
        @Path("id") accountId: String,
    ): ApiResult<Relationship>

    @POST("api/v1/accounts/{id}/block")
    suspend fun blockAccount(
        @Path("id") accountId: String,
    ): ApiResult<Relationship>

    @POST("api/v1/accounts/{id}/unblock")
    suspend fun unblockAccount(
        @Path("id") accountId: String,
    ): ApiResult<Relationship>

    @FormUrlEncoded
    @POST("api/v1/accounts/{id}/mute")
    suspend fun muteAccount(
        @Path("id") accountId: String,
        @Field("notifications") notifications: Boolean? = null,
        @Field("duration") duration: Int? = null,
    ): ApiResult<Relationship>

    @POST("api/v1/accounts/{id}/unmute")
    suspend fun unmuteAccount(
        @Path("id") accountId: String,
    ): ApiResult<Relationship>

    @GET("api/v1/accounts/relationships")
    suspend fun relationships(
        @Query("id[]") accountIds: List<String>,
    ): ApiResult<List<Relationship>>

    @POST("api/v1/pleroma/accounts/{id}/subscribe")
    suspend fun subscribeAccount(
        @Path("id") accountId: String,
    ): ApiResult<Relationship>

    @POST("api/v1/pleroma/accounts/{id}/unsubscribe")
    suspend fun unsubscribeAccount(
        @Path("id") accountId: String,
    ): ApiResult<Relationship>

    @GET("api/v1/blocks")
    suspend fun blocks(
        @Query("max_id") maxId: String?,
    ): ApiResult<List<TimelineAccount>>

    @GET("api/v1/mutes")
    suspend fun mutes(
        @Query("max_id") maxId: String?,
    ): ApiResult<List<TimelineAccount>>

    @GET("api/v1/domain_blocks")
    suspend fun domainBlocks(
        @Query("max_id") maxId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("limit") limit: Int? = null,
    ): ApiResult<List<String>>

    @FormUrlEncoded
    @POST("api/v1/domain_blocks")
    suspend fun blockDomain(
        @Field("domain") domain: String,
    ): ApiResult<Unit>

    @FormUrlEncoded
    // @DELETE doesn't support fields
    @HTTP(method = "DELETE", path = "api/v1/domain_blocks", hasBody = true)
    suspend fun unblockDomain(@Field("domain") domain: String): ApiResult<Unit>

    @GET("api/v1/favourites")
    suspend fun favourites(
        @Query("max_id") maxId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("min_id") minId: String? = null,
        @Query("limit") limit: Int?,
    ): ApiResult<List<Status>>

    @GET("api/v1/bookmarks")
    suspend fun bookmarks(
        @Query("max_id") maxId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("min_id") minId: String? = null,
        @Query("limit") limit: Int?,
    ): ApiResult<List<Status>>

    @GET("api/v1/follow_requests")
    suspend fun followRequests(
        @Query("max_id") maxId: String?,
    ): ApiResult<List<TimelineAccount>>

    @POST("api/v1/follow_requests/{id}/authorize")
    suspend fun authorizeFollowRequest(
        @Path("id") accountId: String,
    ): ApiResult<Relationship>

    @POST("api/v1/follow_requests/{id}/reject")
    suspend fun rejectFollowRequest(
        @Path("id") accountId: String,
    ): ApiResult<Relationship>

    @FormUrlEncoded
    @POST("api/v1/apps")
    suspend fun authenticateApp(
        @Header(DOMAIN_HEADER) domain: String,
        @Field("client_name") clientName: String,
        @Field("redirect_uris") redirectUris: String,
        @Field("scopes") scopes: String,
        @Field("website") website: String,
    ): ApiResult<AppCredentials>

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun fetchOAuthToken(
        @Header(DOMAIN_HEADER) domain: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("code") code: String,
        @Field("grant_type") grantType: String,
    ): ApiResult<AccessToken>

    @FormUrlEncoded
    @POST("oauth/revoke")
    suspend fun revokeOAuthToken(
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("token") token: String,
    ): ApiResult<Unit>

    @GET("/api/v1/lists")
    suspend fun getLists(): ApiResult<List<MastoList>>

    @GET("/api/v1/accounts/{id}/lists")
    suspend fun getListsIncludesAccount(
        @Path("id") accountId: String,
    ): ApiResult<List<MastoList>>

    @FormUrlEncoded
    @POST("api/v1/lists")
    suspend fun createList(
        @Field("title") title: String,
        @Field("exclusive") exclusive: Boolean?,
        @Field("replies_policy") repliesPolicy: UserListRepliesPolicy = UserListRepliesPolicy.LIST,
    ): ApiResult<MastoList>

    @FormUrlEncoded
    @PUT("api/v1/lists/{listId}")
    suspend fun updateList(
        @Path("listId") listId: String,
        @Field("title") title: String,
        @Field("exclusive") exclusive: Boolean?,
        @Field("replies_policy") repliesPolicy: UserListRepliesPolicy = UserListRepliesPolicy.LIST,
    ): ApiResult<MastoList>

    @DELETE("api/v1/lists/{listId}")
    suspend fun deleteList(
        @Path("listId") listId: String,
    ): ApiResult<Unit>

    @GET("api/v1/lists/{listId}/accounts")
    suspend fun getAccountsInList(
        @Path("listId") listId: String,
        @Query("limit") limit: Int,
    ): ApiResult<List<TimelineAccount>>

    @FormUrlEncoded
    // @DELETE doesn't support fields
    @HTTP(method = "DELETE", path = "api/v1/lists/{listId}/accounts", hasBody = true)
    suspend fun deleteAccountFromList(
        @Path("listId") listId: String,
        @Field("account_ids[]") accountIds: List<String>,
    ): ApiResult<Unit>

    @FormUrlEncoded
    @POST("api/v1/lists/{listId}/accounts")
    suspend fun addAccountToList(
        @Path("listId") listId: String,
        @Field("account_ids[]") accountIds: List<String>,
    ): ApiResult<Unit>

    @GET("/api/v1/conversations")
    suspend fun getConversations(
        @Query("max_id") maxId: String? = null,
        @Query("limit") limit: Int? = null,
    ): ApiResult<List<Conversation>>

    @DELETE("/api/v1/conversations/{id}")
    suspend fun deleteConversation(
        @Path("id") conversationId: String,
    ): ApiResult<Unit>

    @FormUrlEncoded
    @POST("api/v1/filters")
    suspend fun createFilterV1(
        @Field("phrase") phrase: String,
        @Field("context[]") context: Set<FilterContext>,
        @Field("irreversible") irreversible: Boolean?,
        @Field("whole_word") wholeWord: Boolean?,
        // String not Int because the empty string is used to represent "indefinite",
        // see https://github.com/mastodon/documentation/issues/1216#issuecomment-2030222940
        @Field("expires_in") expiresInSeconds: String?,
    ): ApiResult<FilterV1>

    @FormUrlEncoded
    @PUT("api/v1/filters/{id}")
    suspend fun updateFilterV1(
        @Path("id") id: String,
        @Field("phrase") phrase: String,
        @Field("context[]") contexts: Collection<FilterContext>,
        @Field("irreversible") irreversible: Boolean?,
        @Field("whole_word") wholeWord: Boolean?,
        // String not Int because the empty string is used to represent "indefinite",
        // see https://github.com/mastodon/documentation/issues/1216#issuecomment-2030222940
        @Field("expires_in") expiresInSeconds: String?,
    ): ApiResult<FilterV1>

    @DELETE("api/v1/filters/{id}")
    suspend fun deleteFilterV1(
        @Path("id") id: String,
    ): ApiResult<Unit>

    @Headers("Content-Type: application/x-www-form-urlencoded")
    @POST("api/v2/filters")
    suspend fun createFilter(@Body newContentFilter: NewContentFilter): ApiResult<Filter>

    @FormUrlEncoded
    @PUT("api/v2/filters/{id}")
    suspend fun updateFilter(
        @Path("id") id: String,
        @Field("title") title: String? = null,
        @Field("context[]") contexts: Collection<FilterContext>? = null,
        @Field("filter_action") filterAction: FilterAction? = null,
        // String not Int because the empty string is used to represent "indefinite",
        // see https://github.com/mastodon/documentation/issues/1216#issuecomment-2030222940
        @Field("expires_in") expiresInSeconds: String? = null,
    ): ApiResult<Filter>

    @DELETE("api/v2/filters/{id}")
    suspend fun deleteFilter(
        @Path("id") id: String,
    ): ApiResult<Unit>

    @FormUrlEncoded
    @POST("api/v2/filters/{filterId}/keywords")
    suspend fun addFilterKeyword(
        @Path("filterId") filterId: String,
        @Field("keyword") keyword: String,
        @Field("whole_word") wholeWord: Boolean,
    ): ApiResult<FilterKeyword>

    @FormUrlEncoded
    @PUT("api/v2/filters/keywords/{keywordId}")
    suspend fun updateFilterKeyword(
        @Path("keywordId") keywordId: String,
        @Field("keyword") keyword: String,
        @Field("whole_word") wholeWord: Boolean,
    ): ApiResult<FilterKeyword>

    @DELETE("api/v2/filters/keywords/{keywordId}")
    suspend fun deleteFilterKeyword(
        @Path("keywordId") keywordId: String,
    ): ApiResult<Unit>

    @FormUrlEncoded
    @POST("api/v1/polls/{id}/votes")
    suspend fun voteInPoll(
        @Path("id") id: String,
        @Field("choices[]") choices: List<Int>,
    ): ApiResult<Poll>

    @GET("api/v1/announcements")
    suspend fun listAnnouncements(
        @Query("with_dismissed") withDismissed: Boolean = true,
    ): ApiResult<List<Announcement>>

    @POST("api/v1/announcements/{id}/dismiss")
    suspend fun dismissAnnouncement(
        @Path("id") announcementId: String,
    ): ApiResult<Unit>

    @PUT("api/v1/announcements/{id}/reactions/{name}")
    suspend fun addAnnouncementReaction(
        @Path("id") announcementId: String,
        @Path("name") name: String,
    ): ApiResult<Unit>

    @DELETE("api/v1/announcements/{id}/reactions/{name}")
    suspend fun removeAnnouncementReaction(
        @Path("id") announcementId: String,
        @Path("name") name: String,
    ): ApiResult<Unit>

    @FormUrlEncoded
    @POST("api/v1/reports")
    suspend fun report(
        @Field("account_id") accountId: String,
        @Field("status_ids[]") statusIds: List<String>,
        @Field("comment") comment: String,
        @Field("forward") isNotifyRemote: Boolean?,
    ): ApiResult<Unit>

    @GET("api/v2/search")
    suspend fun search(
        @Query("q") query: String?,
        @Query("type") type: String? = null,
        @Query("resolve") resolve: Boolean? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("following") following: Boolean? = null,
    ): ApiResult<SearchResult>

    @FormUrlEncoded
    @POST("api/v1/accounts/{id}/note")
    suspend fun updateAccountNote(
        @Path("id") accountId: String,
        @Field("comment") note: String,
    ): ApiResult<Relationship>

    @FormUrlEncoded
    @POST("api/v1/push/subscription")
    suspend fun subscribePushNotifications(
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String,
        @Field("subscription[endpoint]") endPoint: String,
        @Field("subscription[keys][p256dh]") keysP256DH: String,
        @Field("subscription[keys][auth]") keysAuth: String,
        // The "data[alerts][]" fields to enable / disable notifications
        // Should be generated dynamically from all the available notification
        // types defined in [app.pachli.entities.Notification.Types]
        @FieldMap data: Map<String, Boolean>,
    ): ApiResult<NotificationSubscribeResult>

    @DELETE("api/v1/push/subscription")
    suspend fun unsubscribePushNotifications(
        @Header("Authorization") auth: String,
        @Header(DOMAIN_HEADER) domain: String,
    ): ApiResult<Unit>

    @GET("api/v1/tags/{name}")
    suspend fun tag(@Path("name") name: String): ApiResult<HashTag>

    @GET("api/v1/followed_tags")
    suspend fun followedTags(
        @Query("min_id") minId: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("max_id") maxId: String? = null,
        @Query("limit") limit: Int? = null,
    ): ApiResult<List<HashTag>>

    @POST("api/v1/tags/{name}/follow")
    suspend fun followTag(@Path("name") name: String): ApiResult<HashTag>

    @POST("api/v1/tags/{name}/unfollow")
    suspend fun unfollowTag(@Path("name") name: String): ApiResult<HashTag>

    @GET("api/v1/trends/tags")
    suspend fun trendingTags(
        @Query("limit") limit: Int? = null,
    ): ApiResult<List<TrendingTag>>

    @GET("api/v1/trends/links")
    suspend fun trendingLinks(
        @Query("limit") limit: Int? = null,
    ): ApiResult<List<TrendsLink>>

    @GET("api/v1/trends/statuses")
    suspend fun trendingStatuses(
        @Query("limit") limit: Int? = null,
    ): ApiResult<List<Status>>

    @GET("api/v2/suggestions")
    suspend fun getSuggestions(
        @Query("limit") limit: Int? = null,
    ): ApiResult<List<Suggestion>>

    @DELETE("api/v1/suggestions/{accountId}")
    suspend fun deleteSuggestion(@Path("accountId") accountId: String): ApiResult<Unit>

    // Copy of followAccount, except it returns an ApiResult. Temporary, until followAccount
    // is converted to also return ApiResult.
    @POST("api/v1/accounts/{id}/follow")
    suspend fun followSuggestedAccount(@Path("id") accountId: String): ApiResult<Relationship>
}

/* Copyright 2018 Conny Duck
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

package app.pachli.core.database

import androidx.room3.ColumnTypeConverter
import androidx.room3.ProvidedColumnTypeConverter
import app.pachli.core.database.model.TimelineStatusEntity
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.Announcement
import app.pachli.core.model.Attachment
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.Card
import app.pachli.core.model.CollectionItem
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.ConversationAccount
import app.pachli.core.model.DraftAttachment
import app.pachli.core.model.Emoji
import app.pachli.core.model.Field
import app.pachli.core.model.FilterResult
import app.pachli.core.model.Hashtag
import app.pachli.core.model.HashtagHistory
import app.pachli.core.model.MovedAccount
import app.pachli.core.model.NewPoll
import app.pachli.core.model.Poll
import app.pachli.core.model.Role
import app.pachli.core.model.ServerLimits
import app.pachli.core.model.ServerOperation
import app.pachli.core.model.ShallowHashtag
import app.pachli.core.model.Status
import app.pachli.core.model.Timeline
import app.pachli.core.model.TranslatedAttachment
import app.pachli.core.model.TranslatedPoll
import app.pachli.core.model.collection.CollectionDisplayAction
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import io.github.z4kn4fein.semver.Version
import java.net.URLDecoder
import java.time.Instant
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Better version of Moshi's [JsonEncodingException].
 *
 * The error message includes the specific JSON that failed to decode, and the
 * type we're trying to decode to. Much more useful when troubleshooting errors.
 *
 * @property json The JSON that failed to decode.
 * @property type Name of the target type.
 * @property cause
 */
data class BetterJsonException(
    val json: String?,
    val type: String,
    override val cause: Throwable,
) : Exception() {
    override fun getLocalizedMessage(): String {
        return "«${cause.localizedMessage}»: failed JSON: «$json», type: «$type»"
    }
}

@OptIn(ExperimentalStdlibApi::class)
@ProvidedColumnTypeConverter
@Singleton
class Converters @Inject constructor(
    private val moshi: Moshi,
) {
    /** Serialises [value] to JSON. */
    private inline fun <reified T> toJson(value: T): String = moshi.adapter<T>().toJson(value)

    /**
     * Deserialises [json] to [T].
     *
     * @throws BetterJsonException if deserialisation fails.
     */
    private inline fun <reified T> fromJson(json: String?): T? {
        return try {
            json?.let { moshi.adapter<T>().fromJson(it) }
        } catch (e: JsonEncodingException) {
            throw BetterJsonException(json, T::class.java.name, e)
        } catch (e: JsonDataException) {
            throw BetterJsonException(json, T::class.java.name, e)
        }
    }

    @ColumnTypeConverter
    fun jsonToEmojiList(json: String?): List<Emoji>? = fromJson(json)

    @ColumnTypeConverter
    fun emojiListToJson(emojiList: List<Emoji>?) = toJson(emojiList)

    @ColumnTypeConverter
    fun visibilityToInt(visibility: Status.Visibility?): Int {
        return visibility?.ordinal ?: Status.Visibility.UNKNOWN.ordinal
    }

    @ColumnTypeConverter
    fun intToVisibility(visibility: Int): Status.Visibility {
        return Status.Visibility.getOrUnknown(visibility)
    }

    @ColumnTypeConverter
    fun stringToTimeline(str: String?): List<Timeline>? {
        str ?: return null

        // Two possible storage formats. Newer (from Pachli 2.4.0) is polymorphic
        // JSON, and the first character will be a '['
        if (str.startsWith('[')) {
            return moshi.adapter<List<Timeline>>().fromJson(str)
        }

        // Older is string of ';' delimited tuples, one per tab.
        // The per-tab data is ':' delimited tuples where the first item is the tab's kind,
        // any subsequent entries are tab-specific data.
        //
        // The "Trending_..." / "Trending..."  is to work around
        // https://github.com/pachli/pachli-android/issues/329
        return str.split(";").map {
            val data = it.split(":")
            val kind = data[0]
            val arguments = data.drop(1).map { s -> URLDecoder.decode(s, "UTF-8") }

            when (kind) {
                "Home" -> Timeline.Home
                "Notifications" -> Timeline.Notifications
                "Local" -> Timeline.PublicLocal
                "Federated" -> Timeline.PublicFederated
                "Direct" -> Timeline.Conversations
                // Work around for https://github.com/pachli/pachli-android/issues/329
                // when the Trending... kinds may have been serialised without the '_'
                "TrendingTags", "Trending_Tags" -> Timeline.TrendingHashtags
                "TrendingLinks", "Trending_Links" -> Timeline.TrendingLinks
                "TrendingStatuses", "Trending_Statuses" -> Timeline.TrendingStatuses
                "Hashtag" -> Timeline.Hashtags(arguments)
                "List" -> Timeline.UserList(arguments[0], arguments[1])
                "Bookmarks" -> Timeline.Bookmarks
                else -> throw IllegalStateException("Unrecognised tab kind: $kind")
            }
        }
    }

    @ColumnTypeConverter
    fun timelineToString(timelines: List<Timeline>?) = toJson(timelines)

    @ColumnTypeConverter
    fun accountToJson(account: ConversationAccount?) = toJson(account)

    @ColumnTypeConverter
    fun jsonToAccount(accountJson: String?) = fromJson<ConversationAccount>(accountJson)

    @ColumnTypeConverter
    fun accountListToJson(accountList: List<ConversationAccount>?) = toJson(accountList)

    @ColumnTypeConverter
    fun jsonToAccountList(accountListJson: String?) = fromJson<List<ConversationAccount>>(accountListJson)

    @ColumnTypeConverter
    fun attachmentListToJson(attachmentList: List<Attachment>?) = toJson(attachmentList)

    @ColumnTypeConverter
    fun jsonToAttachmentList(attachmentListJson: String?) = fromJson<List<Attachment>>(attachmentListJson)

    @ColumnTypeConverter
    fun mentionListToJson(mentionArray: List<Status.Mention>?) = toJson(mentionArray)

    @ColumnTypeConverter
    fun jsonToMentionArray(mentionListJson: String?) = fromJson<List<Status.Mention>>(mentionListJson)

    @ColumnTypeConverter
    fun tagListToJson(tagArray: List<Hashtag>?) = toJson(tagArray)

    @ColumnTypeConverter
    fun jsonToTagArray(tagListJson: String?) = fromJson<List<Hashtag>>(tagListJson)

    @ColumnTypeConverter
    fun dateToLong(date: Date?) = date?.time

    @ColumnTypeConverter
    fun longToDate(date: Long?) = date?.let { Date(it) }

    @ColumnTypeConverter
    fun instantToEpochMilli(instant: Instant?) = instant?.toEpochMilli()

    @ColumnTypeConverter
    fun epochMilliToInstant(epochMilli: Long?) = epochMilli?.let { Instant.ofEpochMilli(it) }

    @ColumnTypeConverter
    fun pollToJson(poll: Poll?) = toJson(poll)

    @ColumnTypeConverter
    fun jsonToPoll(pollJson: String?) = fromJson<Poll>(pollJson)

    @ColumnTypeConverter
    fun newPollToJson(newPoll: NewPoll?) = toJson(newPoll)

    @ColumnTypeConverter
    fun jsonToNewPoll(newPollJson: String?) = fromJson<NewPoll>(newPollJson)

    @ColumnTypeConverter
    fun draftAttachmentListToJson(draftAttachments: List<DraftAttachment>?) = toJson(draftAttachments)

    @ColumnTypeConverter
    fun jsonToDraftAttachmentList(draftAttachmentListJson: String?) = fromJson<List<DraftAttachment>>(draftAttachmentListJson)

    @ColumnTypeConverter
    fun filterResultListToJson(filterResults: List<FilterResult>?) = toJson(filterResults)

    @ColumnTypeConverter
    fun jsonToFilterResultList(filterResultListJson: String?) = fromJson<List<FilterResult>>(filterResultListJson)

    @ColumnTypeConverter
    fun translatedPolltoJson(translatedPoll: TranslatedPoll?) = toJson(translatedPoll)

    @ColumnTypeConverter
    fun jsonToTranslatedPoll(translatedPollJson: String?) = fromJson<TranslatedPoll>(translatedPollJson)

    @ColumnTypeConverter
    fun translatedAttachmentToJson(translatedAttachment: List<TranslatedAttachment>?) = toJson(translatedAttachment)

    @ColumnTypeConverter
    fun jsonToTranslatedAttachment(translatedAttachmentJson: String) = fromJson<List<TranslatedAttachment>>(translatedAttachmentJson)

    @ColumnTypeConverter
    fun throwableToString(t: Throwable) = t.message

    @ColumnTypeConverter
    fun stringToThrowable(s: String) = Throwable(message = s)

    @ColumnTypeConverter
    fun capabilitiesMapToJson(capabilities: Map<ServerOperation, Version>) = toJson(capabilities)

    @ColumnTypeConverter
    fun jsonToCapabiltiesMap(capabilitiesJson: String?) = fromJson<Map<ServerOperation, Version>>(capabilitiesJson)

    @ColumnTypeConverter
    fun contentFiltersToJson(contentFilters: List<ContentFilter>) = toJson(contentFilters)

    @ColumnTypeConverter
    fun jsonToContentFilters(s: String?) = fromJson<List<ContentFilter>>(s)

    @ColumnTypeConverter
    fun versionToString(version: Version): String = version.toString()

    @ColumnTypeConverter
    fun stringToVersion(s: String?) = s?.let { Version.parse(it) }

    @ColumnTypeConverter
    fun announcementToJson(announcement: Announcement) = toJson(announcement)

    @ColumnTypeConverter
    fun jsonToAnnouncement(s: String?) = fromJson<Announcement>(s)

    @ColumnTypeConverter
    fun applicationToJson(application: Status.Application) = toJson(application)

    @ColumnTypeConverter
    fun jsonToApplication(s: String?) = fromJson<Status.Application>(s)

    @ColumnTypeConverter
    fun cardToJson(card: Card) = toJson(card)

    @ColumnTypeConverter
    fun jsonToCard(s: String?) = fromJson<Card>(s)

    @ColumnTypeConverter
    fun listStringToJson(l: List<String>) = toJson(l)

    @ColumnTypeConverter
    fun stringToListString(s: String?) = fromJson<List<String>>(s)

    @ColumnTypeConverter
    fun listNullableStringToJson(l: List<String?>) = toJson(l)

    @ColumnTypeConverter
    fun stringToListNullableString(s: String?) = fromJson<List<String?>>(s)

    @ColumnTypeConverter
    fun accountFilterDecisionToJson(accountFilterDecision: AccountFilterDecision) = toJson(accountFilterDecision)

    @ColumnTypeConverter
    fun jsonToAccountFilterDecision(s: String?) = fromJson<AccountFilterDecision>(s)

    @ColumnTypeConverter
    fun timelineKindToJson(kind: TimelineStatusEntity.Kind) = toJson(kind)

    @ColumnTypeConverter
    fun jsonToTimelineKind(s: String?) = fromJson<TimelineStatusEntity.Kind>(s)

    @ColumnTypeConverter
    fun draftAttachmentToJson(a: DraftAttachment) = toJson(a)

    @ColumnTypeConverter
    fun jsonToDraftAttachment(s: String?) = fromJson<DraftAttachment>(s)

    @ColumnTypeConverter
    fun listRoleToJson(roles: List<Role>) = toJson(roles)

    @ColumnTypeConverter
    fun jsonToListRoles(s: String?) = fromJson<List<Role>>(s)

    @ColumnTypeConverter
    fun attachmentDisplayActionToJson(attachmentDisplayAction: AttachmentDisplayAction) = toJson(attachmentDisplayAction)

    @ColumnTypeConverter
    fun jsonToAttachmentDisplayAction(s: String?) = fromJson<AttachmentDisplayAction>(s)

    @ColumnTypeConverter
    fun quoteApprovalToJson(quoteApproval: Status.QuoteApproval) = toJson(quoteApproval)

    @ColumnTypeConverter
    fun jsonToQuoteApproval(s: String?) = fromJson<Status.QuoteApproval>(s)

    @ColumnTypeConverter
    fun hashtagHistoryToJson(hashtagHistory: List<HashtagHistory>) = toJson(hashtagHistory)

    @ColumnTypeConverter
    fun jsonToHashtagHistory(s: String?) = fromJson<List<HashtagHistory>>(s)

    @ColumnTypeConverter
    fun serverLimitsToJson(serverLimits: ServerLimits) = toJson(serverLimits)

    @ColumnTypeConverter
    fun jsonToServerLimits(s: String?) = fromJson<ServerLimits>(s)

    @ColumnTypeConverter
    fun listFieldToJson(fields: List<Field>) = toJson(fields)

    @ColumnTypeConverter
    fun jsonToListField(s: String?) = fromJson<List<Field>>(s)

    @ColumnTypeConverter
    fun movedAccountToJson(movedAccount: MovedAccount) = toJson(movedAccount)

    @ColumnTypeConverter
    fun jsonToMovedAccount(s: String?) = fromJson<MovedAccount>(s)

    @ColumnTypeConverter
    fun collectionItemsToJson(collectionItems: List<CollectionItem>) = toJson(collectionItems)

    @ColumnTypeConverter
    fun jsonToCollectionItems(s: String?) = fromJson<List<CollectionItem>>(s)

    @ColumnTypeConverter
    fun collectionDisplayActionToJson(collectionDisplayAction: CollectionDisplayAction) = toJson(collectionDisplayAction)

    @ColumnTypeConverter
    fun jsonToCollectionDisplayAction(s: String?) = fromJson<CollectionDisplayAction>(s)

    @ColumnTypeConverter
    fun shallowHashtagToJson(shallowHashtag: ShallowHashtag) = toJson(shallowHashtag)

    @ColumnTypeConverter
    fun jsonToShallowHashtag(s: String?) = fromJson<ShallowHashtag>(s)
}

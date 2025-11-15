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

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import app.pachli.core.database.model.TimelineStatusEntity
import app.pachli.core.model.AccountFilterDecision
import app.pachli.core.model.Announcement
import app.pachli.core.model.Attachment
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.Card
import app.pachli.core.model.ContentFilter
import app.pachli.core.model.ConversationAccount
import app.pachli.core.model.DraftAttachment
import app.pachli.core.model.Emoji
import app.pachli.core.model.FilterResult
import app.pachli.core.model.HashTag
import app.pachli.core.model.NewPoll
import app.pachli.core.model.Poll
import app.pachli.core.model.Role
import app.pachli.core.model.ServerOperation
import app.pachli.core.model.Status
import app.pachli.core.model.Timeline
import app.pachli.core.model.TranslatedAttachment
import app.pachli.core.model.TranslatedPoll
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
data class BetterJsonEncodingException(
    val json: String?,
    val type: String,
    override val cause: JsonEncodingException,
) : Exception() {
    override fun getLocalizedMessage(): String {
        return "«${cause.localizedMessage}»: failed JSON: «$json», type: «$type»"
    }
}

@OptIn(ExperimentalStdlibApi::class)
@ProvidedTypeConverter
@Singleton
class Converters @Inject constructor(
    private val moshi: Moshi,
) {
    /** Serialises [value] to JSON. */
    private inline fun <reified T> toJson(value: T): String = moshi.adapter<T>().toJson(value)

    /**
     * Deserialises [json] to [T].
     *
     * @throws BetterJsonEncodingException if deserialisation fails.
     */
    private inline fun <reified T> fromJson(json: String?): T? {
        return try {
            json?.let { moshi.adapter<T>().fromJson(it) }
        } catch (e: JsonEncodingException) {
            throw BetterJsonEncodingException(json, T::class.java.name, e)
        }
    }

    @TypeConverter
    fun jsonToEmojiList(json: String?): List<Emoji>? = fromJson(json)

    @TypeConverter
    fun emojiListToJson(emojiList: List<Emoji>?) = toJson(emojiList)

    @TypeConverter
    fun visibilityToInt(visibility: Status.Visibility?): Int {
        return visibility?.ordinal ?: Status.Visibility.UNKNOWN.ordinal
    }

    @TypeConverter
    fun intToVisibility(visibility: Int): Status.Visibility {
        return Status.Visibility.getOrUnknown(visibility)
    }

    @TypeConverter
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

    @TypeConverter
    fun timelineToString(timelines: List<Timeline>?) = toJson(timelines)

    @TypeConverter
    fun accountToJson(account: ConversationAccount?) = toJson(account)

    @TypeConverter
    fun jsonToAccount(accountJson: String?) = fromJson<ConversationAccount>(accountJson)

    @TypeConverter
    fun accountListToJson(accountList: List<ConversationAccount>?) = toJson(accountList)

    @TypeConverter
    fun jsonToAccountList(accountListJson: String?) = fromJson<List<ConversationAccount>>(accountListJson)

    @TypeConverter
    fun attachmentListToJson(attachmentList: List<Attachment>?) = toJson(attachmentList)

    @TypeConverter
    fun jsonToAttachmentList(attachmentListJson: String?) = fromJson<List<Attachment>>(attachmentListJson)

    @TypeConverter
    fun mentionListToJson(mentionArray: List<Status.Mention>?) = toJson(mentionArray)

    @TypeConverter
    fun jsonToMentionArray(mentionListJson: String?) = fromJson<List<Status.Mention>>(mentionListJson)

    @TypeConverter
    fun tagListToJson(tagArray: List<HashTag>?) = toJson(tagArray)

    @TypeConverter
    fun jsonToTagArray(tagListJson: String?) = fromJson<List<HashTag>>(tagListJson)

    @TypeConverter
    fun dateToLong(date: Date?) = date?.time

    @TypeConverter
    fun longToDate(date: Long?) = date?.let { Date(it) }

    @TypeConverter
    fun instantToEpochMilli(instant: Instant?) = instant?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(epochMilli: Long?) = epochMilli?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun pollToJson(poll: Poll?) = toJson(poll)

    @TypeConverter
    fun jsonToPoll(pollJson: String?) = fromJson<Poll>(pollJson)

    @TypeConverter
    fun newPollToJson(newPoll: NewPoll?) = toJson(newPoll)

    @TypeConverter
    fun jsonToNewPoll(newPollJson: String?) = fromJson<NewPoll>(newPollJson)

    @TypeConverter
    fun draftAttachmentListToJson(draftAttachments: List<DraftAttachment>?) = toJson(draftAttachments)

    @TypeConverter
    fun jsonToDraftAttachmentList(draftAttachmentListJson: String?) = fromJson<List<DraftAttachment>>(draftAttachmentListJson)

    @TypeConverter
    fun filterResultListToJson(filterResults: List<FilterResult>?) = toJson(filterResults)

    @TypeConverter
    fun jsonToFilterResultList(filterResultListJson: String?) = fromJson<List<FilterResult>>(filterResultListJson)

    @TypeConverter
    fun translatedPolltoJson(translatedPoll: TranslatedPoll?) = toJson(translatedPoll)

    @TypeConverter
    fun jsonToTranslatedPoll(translatedPollJson: String?) = fromJson<TranslatedPoll>(translatedPollJson)

    @TypeConverter
    fun translatedAttachmentToJson(translatedAttachment: List<TranslatedAttachment>?) = toJson(translatedAttachment)

    @TypeConverter
    fun jsonToTranslatedAttachment(translatedAttachmentJson: String) = fromJson<List<TranslatedAttachment>>(translatedAttachmentJson)

    @TypeConverter
    fun throwableToString(t: Throwable) = t.message

    @TypeConverter
    fun stringToThrowable(s: String) = Throwable(message = s)

    @TypeConverter
    fun capabilitiesMapToJson(capabilities: Map<ServerOperation, Version>) = toJson(capabilities)

    @TypeConverter
    fun jsonToCapabiltiesMap(capabilitiesJson: String?) = fromJson<Map<ServerOperation, Version>>(capabilitiesJson)

    @TypeConverter
    fun contentFiltersToJson(contentFilters: List<ContentFilter>) = toJson(contentFilters)

    @TypeConverter
    fun jsonToContentFilters(s: String?) = fromJson<List<ContentFilter>>(s)

    @TypeConverter
    fun versionToString(version: Version): String = version.toString()

    @TypeConverter
    fun stringToVersion(s: String?) = s?.let { Version.parse(it) }

    @TypeConverter
    fun announcementToJson(announcement: Announcement) = toJson(announcement)

    @TypeConverter
    fun jsonToAnnouncement(s: String?) = fromJson<Announcement>(s)

    @TypeConverter
    fun applicationToJson(application: Status.Application) = toJson(application)

    @TypeConverter
    fun jsonToApplication(s: String?) = fromJson<Status.Application>(s)

    @TypeConverter
    fun cardToJson(card: Card) = toJson(card)

    @TypeConverter
    fun jsonToCard(s: String?) = fromJson<Card>(s)

    @TypeConverter
    fun listStringToJson(l: List<String>) = toJson(l)

    @TypeConverter
    fun stringToListString(s: String?) = fromJson<List<String>>(s)

    @TypeConverter
    fun accountFilterDecisionToJson(accountFilterDecision: AccountFilterDecision) = toJson(accountFilterDecision)

    @TypeConverter
    fun jsonToAccountFilterDecision(s: String?) = fromJson<AccountFilterDecision>(s)

    @TypeConverter
    fun timelineKindToJson(kind: TimelineStatusEntity.Kind) = toJson(kind)

    @TypeConverter
    fun jsonToTimelineKind(s: String?) = fromJson<TimelineStatusEntity.Kind>(s)

    @TypeConverter
    fun draftAttachmentToJson(a: DraftAttachment) = toJson(a)

    @TypeConverter
    fun jsonToDraftAttachment(s: String?) = fromJson<DraftAttachment>(s)

    @TypeConverter
    fun listRoleToJson(roles: List<Role>) = toJson(roles)

    @TypeConverter
    fun jsonToListRoles(s: String?) = fromJson<List<Role>>(s)

    @TypeConverter
    fun attachmentDisplayActionToJson(attachmentDisplayAction: AttachmentDisplayAction) = toJson(attachmentDisplayAction)

    @TypeConverter
    fun jsonToAttachmentDisplayAction(s: String?) = fromJson<AttachmentDisplayAction>(s)

    @TypeConverter
    fun quoteApprovalToJson(quoteApproval: Status.QuoteApproval) = toJson(quoteApproval)

    @TypeConverter
    fun jsonToQuoteApproval(s: String?) = fromJson<Status.QuoteApproval>(s)
}

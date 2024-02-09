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
import app.pachli.core.database.model.ConversationAccountEntity
import app.pachli.core.database.model.DraftAttachment
import app.pachli.core.database.model.TabData
import app.pachli.core.network.model.Attachment
import app.pachli.core.network.model.Emoji
import app.pachli.core.network.model.FilterResult
import app.pachli.core.network.model.HashTag
import app.pachli.core.network.model.NewPoll
import app.pachli.core.network.model.Poll
import app.pachli.core.network.model.Status
import app.pachli.core.network.model.TranslatedAttachment
import app.pachli.core.network.model.TranslatedPoll
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalStdlibApi::class)
@ProvidedTypeConverter
@Singleton
class Converters @Inject constructor(
    private val moshi: Moshi,
) {

    @TypeConverter
    fun jsonToEmojiList(json: String?): List<Emoji>? {
        return json?.let { moshi.adapter<List<Emoji>>().fromJson(it) }
    }

    @TypeConverter
    fun emojiListToJson(emojiList: List<Emoji>?): String {
        return moshi.adapter<List<Emoji>?>().toJson(emojiList)
    }

    @TypeConverter
    fun visibilityToInt(visibility: Status.Visibility?): Int {
        return visibility?.num ?: Status.Visibility.UNKNOWN.num
    }

    @TypeConverter
    fun intToVisibility(visibility: Int): Status.Visibility {
        return Status.Visibility.byNum(visibility)
    }

    @TypeConverter
    fun stringToTabData(str: String?): List<TabData>? {
        return str?.split(";")
            ?.map {
                val data = it.split(":")
                TabData.from(data[0], data.drop(1).map { s -> URLDecoder.decode(s, "UTF-8") })
            }
    }

    @TypeConverter
    fun tabDataToString(tabData: List<TabData>?): String? {
        // List name may include ":"
        return tabData?.joinToString(";") { it.kind.repr + ":" + it.arguments.joinToString(":") { s -> URLEncoder.encode(s, "UTF-8") } }
    }

    @TypeConverter
    fun accountToJson(account: ConversationAccountEntity?): String {
        return moshi.adapter<ConversationAccountEntity>().toJson(account)
    }

    @TypeConverter
    fun jsonToAccount(accountJson: String?): ConversationAccountEntity? {
        return accountJson?.let { moshi.adapter<ConversationAccountEntity>().fromJson(it) }
    }

    @TypeConverter
    fun accountListToJson(accountList: List<ConversationAccountEntity>?): String {
        return moshi.adapter<List<ConversationAccountEntity>>().toJson(accountList)
    }

    @TypeConverter
    fun jsonToAccountList(accountListJson: String?): List<ConversationAccountEntity>? {
        return accountListJson?.let { moshi.adapter<List<ConversationAccountEntity>?>().fromJson(it) }
    }

    @TypeConverter
    fun attachmentListToJson(attachmentList: List<Attachment>?): String {
        return moshi.adapter<List<Attachment>?>().toJson(attachmentList)
    }

    @TypeConverter
    fun jsonToAttachmentList(attachmentListJson: String?): List<Attachment>? {
        return attachmentListJson?.let { moshi.adapter<List<Attachment>?>().fromJson(it) }
    }

    @TypeConverter
    fun mentionListToJson(mentionArray: List<Status.Mention>?): String? {
        return moshi.adapter<List<Status.Mention>?>().toJson(mentionArray)
    }

    @TypeConverter
    fun jsonToMentionArray(mentionListJson: String?): List<Status.Mention>? {
        return mentionListJson?.let { moshi.adapter<List<Status.Mention>?>().fromJson(it) }
    }

    @TypeConverter
    fun tagListToJson(tagArray: List<HashTag>?): String? {
        return moshi.adapter<List<HashTag>?>().toJson(tagArray)
    }

    @TypeConverter
    fun jsonToTagArray(tagListJson: String?): List<HashTag>? {
        return tagListJson?.let { moshi.adapter<List<HashTag>?>().fromJson(it) }
    }

    @TypeConverter
    fun dateToLong(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun longToDate(date: Long?): Date? {
        return date?.let { Date(it) }
    }

    @TypeConverter
    fun pollToJson(poll: Poll?): String? {
        return moshi.adapter<Poll?>().toJson(poll)
    }

    @TypeConverter
    fun jsonToPoll(pollJson: String?): Poll? {
        return pollJson?.let { moshi.adapter<Poll?>().fromJson(it) }
    }

    @TypeConverter
    fun newPollToJson(newPoll: NewPoll?): String? {
        return moshi.adapter<NewPoll?>().toJson(newPoll)
    }

    @TypeConverter
    fun jsonToNewPoll(newPollJson: String?): NewPoll? {
        return newPollJson?.let { moshi.adapter<NewPoll?>().fromJson(it) }
    }

    @TypeConverter
    fun draftAttachmentListToJson(draftAttachments: List<DraftAttachment>?): String? {
        return moshi.adapter<List<DraftAttachment>?>().toJson(draftAttachments)
    }

    @TypeConverter
    fun jsonToDraftAttachmentList(draftAttachmentListJson: String?): List<DraftAttachment>? {
        return draftAttachmentListJson?.let { moshi.adapter<List<DraftAttachment>?>().fromJson(it) }
    }

    @TypeConverter
    fun filterResultListToJson(filterResults: List<FilterResult>?): String? {
        return moshi.adapter<List<FilterResult>?>().toJson(filterResults)
    }

    @TypeConverter
    fun jsonToFilterResultList(filterResultListJson: String?): List<FilterResult>? {
        return filterResultListJson?.let { moshi.adapter<List<FilterResult>>().fromJson(it) }
    }

    @TypeConverter
    fun translatedPolltoJson(translatedPoll: TranslatedPoll?): String? {
        return moshi.adapter<TranslatedPoll?>().toJson(translatedPoll)
    }

    @TypeConverter
    fun jsonToTranslatedPoll(translatedPollJson: String?): TranslatedPoll? {
        return translatedPollJson?.let { moshi.adapter<TranslatedPoll?>().fromJson(it) }
    }

    @TypeConverter
    fun translatedAttachmentToJson(translatedAttachment: List<TranslatedAttachment>?): String {
        return moshi.adapter<List<TranslatedAttachment>?>().toJson(translatedAttachment)
    }

    @TypeConverter
    fun jsonToTranslatedAttachment(translatedAttachmentJson: String): List<TranslatedAttachment>? {
        return moshi.adapter<List<TranslatedAttachment>?>().fromJson(translatedAttachmentJson)
    }
}

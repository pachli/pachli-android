/*
 * Copyright 2025 Pachli Association
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

package app.pachli.core.data.repository

import android.content.Context
import androidx.core.content.ContextCompat.getString
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import app.pachli.core.common.di.ApplicationScope
import app.pachli.core.data.R
import app.pachli.core.database.dao.DraftDao
import app.pachli.core.database.model.AccountEntity
import app.pachli.core.database.model.DraftEntity
import app.pachli.core.database.model.asModel
import app.pachli.core.model.AccountSource
import app.pachli.core.model.DeletedStatus
import app.pachli.core.model.Draft
import app.pachli.core.model.ScheduledStatus
import app.pachli.core.model.Status
import app.pachli.core.model.Timeline
import app.pachli.core.model.asQuotePolicy
import app.pachli.core.network.model.StatusSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

@Singleton
class DraftsRepository @Inject constructor(
    @ApplicationScope private val externalScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val draftDao: DraftDao,
) {
    fun getDrafts(pachliAccountId: Long): Flow<PagingData<Draft>> {
        return Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = { draftDao.draftsPagingSource(pachliAccountId) },
        ).flow.map { it.map { it.asModel() } }
    }

    fun deleteDraftAndAttachments(pachliAccountId: Long, draftId: Long) = externalScope.launch {
        val draft = draftDao.find(pachliAccountId, draftId) ?: return@launch
        draft.attachments.forEach { attachment ->
            if (context.contentResolver.delete(attachment.uri, null, null) == 0) {
                Timber.e("Did not delete file %s", attachment.uri)
            }
        }
        draftDao.delete(pachliAccountId, draft.id)
    }

    fun deleteDraftAndAttachments(pachliAccountId: Long, draft: Draft) = externalScope.launch(Dispatchers.IO) {
        draft.attachments.forEach { attachment ->
            if (context.contentResolver.delete(attachment.uri, null, null) == 0) {
                Timber.e("Did not delete file %s", attachment.uri)
            }
        }
        draftDao.delete(pachliAccountId, draft.id)
    }

    /**
     * Upserts [draft] to the local database.
     *
     * If [draft.id][Draft.id] is `0` the draft is saved as a new draft with a new
     * ID, otherwise the draft with that ID is overwritten.
     *
     * @return The saved draft. If [draft.id][Draft.id] was 0 the returned draft
     * contains the new ID, otherwise the draft is returned unchanged.
     */
    suspend fun upsertDraft(pachliAccountId: Long, draft: Draft): Draft = externalScope.async {
        val entity = draft.asEntity(pachliAccountId)
        // TODO: Should saving the draft clear the failure state?
        // Pro: The new version of the draft hasn't been sent before, so the failure message
        // might be obsolete.
        // Con: The user might still need the failure message (e.g., troubleshooting,
        // forwarding to their server admins, etc...)
        val id = draftDao.upsert(entity)

        // If this was an insert Room returns the ID, which must be used in a copy
        // of the draft. Otherwise, Room returns -1L, and the original draft can be
        // returned.
        return@async if (id != -1L) draft.copy(id = id) else draft
    }.await()

    suspend fun updateFailureState(pachliAccountId: Long, draftId: Long, failureMessage: String?, state: Draft.State) = externalScope.async {
        draftDao.updateFailureState(pachliAccountId, draftId, failureMessage, state)
    }.await()

    suspend fun updateDraftState(pachliAccountId: Long, draftId: Long, state: Draft.State) = externalScope.async {
        draftDao.updateState(pachliAccountId, draftId, state)
    }.await()

    fun resetEditingState() = externalScope.launch {
        draftDao.resetEditingState()
    }
}

private fun Draft.asEntity(pachliAccountId: Long) = DraftEntity(
    pachliAccountId = pachliAccountId,
    id = id,
    contentWarning = contentWarning,
    content = content,
    inReplyToId = inReplyToId,
    sensitive = sensitive,
    visibility = visibility,
    attachments = attachments,
    poll = poll,
    failureMessage = failureMessage,
    scheduledAt = scheduledAt,
    language = language,
    statusId = statusId,
    quotePolicy = quotePolicy,
    quotedStatusId = quotedStatusId,
    cursorPosition = cursorPosition,
    state = state,
)

// Note: Caller must still set attachments on ComposeOptions
fun Status.asDraft(source: StatusSource): Draft {
    val actionable = this.actionableStatus

    return Draft(
        id = 0,
        contentWarning = source.spoilerText,
        content = source.text,
        sensitive = actionable.sensitive,
        visibility = actionable.visibility,
        attachments = emptyList(),
        poll = actionable.poll?.toNewPoll(createdAt),
        failureMessage = null,
        scheduledAt = null,
        language = actionable.language,
        quotePolicy = actionable.quoteApproval.asQuotePolicy(),
        statusId = actionable.statusId,
        inReplyToId = actionable.inReplyToId,
        quotedStatusId = (actionable.quote as? Status.Quote.WithStatusId)?.statusId,
        cursorPosition = source.text.length,
        state = Draft.State.DEFAULT,
    )
}

// Note: Caller must still set attachments on ComposeOptions
fun ScheduledStatus.asDraft(): Draft {
    return Draft(
        id = 0,
        contentWarning = params.spoilerText,
        content = params.text,
        sensitive = params.sensitive == true,
        attachments = emptyList(),
        poll = params.poll,
        failureMessage = null,
        visibility = params.visibility,
        language = params.language,
        quotePolicy = params.quotePolicy,
        scheduledAt = scheduledAt,
        statusId = id,
        inReplyToId = params.inReplyToId,
        quotedStatusId = params.quotedStatusId,
        cursorPosition = params.text.length,
        state = Draft.State.DEFAULT,
    )
}

// Note: Caller must still set attachments on ComposeOptions
fun DeletedStatus.asDraft() = Draft(
    id = 0,
    contentWarning = spoilerText,
    content = text.orEmpty(),
    sensitive = sensitive,
    attachments = emptyList(),
    poll = poll?.toNewPoll(createdAt),
    failureMessage = null,
    visibility = visibility,
    language = language,
    quotePolicy = quoteApproval?.asQuotePolicy() ?: AccountSource.QuotePolicy.NOBODY,
    scheduledAt = null,
    statusId = id,
    inReplyToId = inReplyToId,
    quotedStatusId = (quote as? Status.Quote.WithStatusId)?.statusId,
    cursorPosition = text?.length ?: 0,
    state = Draft.State.DEFAULT,
)

/**
 * Creates a draft with visibility and content adjusted for [timeline].
 *
 * If [timeline] is [Timeline.Conversations] the visibility is [Status.Visibility.DIRECT],
 * otherwise the user's [defaultPostPrivacy][AccountEntity.defaultPostPrivacy] is used.
 *
 * if [timeline] is [Timeline.Hashtags] the first hashtag in the timeline's definition
 * is inserted as the first content, with a leading space.
 *
 * The cursor is placed at the start of the content, so the inserted hashtag defaults
 * to appearing at the end of the content when the user starts typing.
 */
fun Draft.Companion.createDraft(context: Context, pachliAccountEntity: AccountEntity, timeline: Timeline): Draft {
    val visibility = when (timeline) {
        Timeline.Conversations -> Status.Visibility.DIRECT
        else -> pachliAccountEntity.defaultPostPrivacy
    }

    val content = when (timeline) {
        is Timeline.Hashtags -> {
            val tag = timeline.tags.first()
            getString(context, R.string.title_tag_with_initial_position).format(tag)
        }

        else -> ""
    }

    val draft = Draft(
        id = 0,
        contentWarning = "",
        content = content,
        sensitive = pachliAccountEntity.defaultMediaSensitivity,
        attachments = emptyList(),
        poll = null,
        failureMessage = null,
        visibility = visibility,
        language = pachliAccountEntity.defaultPostLanguage,
        quotePolicy = pachliAccountEntity.defaultQuotePolicy.clampToVisibility(visibility),
        scheduledAt = null,
        statusId = null,
        inReplyToId = null,
        quotedStatusId = null,
        cursorPosition = 0,
        state = Draft.State.DEFAULT,
    )

    return draft
}

/**
 * Creates a draft reply to [status].
 *
 * The reply inherits some initial values from [status], specifically:
 *
 * - [spoilerText][Status.spoilerText]
 * - [sensitive][Status.sensitive], falling back to the user's [defaultMediaSensitivity][AccountEntity.defaultMediaSensitivity]
 * - [visibility][Status.visibility]
 * - [language][Status.language], falling back to the user's [defaultPostLanguage][AccountEntity.defaultPostLanguage]
 *
 * The initial content is set to @-mention all the accounts @-mentioned in
 * [status] (except for the user's own account).
 *
 * The cursor is placed at the end of the content.
 *
 * @param status Status being replied to.
 */
fun Draft.Companion.createDraftReply(pachliAccountEntity: AccountEntity, status: Status): Draft {
    val actionable = status.actionableStatus
    val account = actionable.account

    val content = buildString {
        LinkedHashSet(
            listOf(account.username) + actionable.mentions.map { it.username },
        ).apply {
            remove(pachliAccountEntity.username)
        }.forEach {
            append('@')
            append(it)
            append(' ')
        }
    }

    val draft = Draft(
        id = 0,
        contentWarning = actionable.spoilerText,
        content = content,
        sensitive = actionable.sensitive || pachliAccountEntity.defaultMediaSensitivity,
        attachments = emptyList(),
        poll = null,
        failureMessage = null,
        visibility = actionable.visibility,
        language = actionable.language ?: pachliAccountEntity.defaultPostLanguage,
        quotePolicy = pachliAccountEntity.defaultQuotePolicy.clampToVisibility(actionable.visibility),
        scheduledAt = null,
        statusId = null,
        inReplyToId = actionable.statusId,
        quotedStatusId = null,
        cursorPosition = content.length,
        state = Draft.State.DEFAULT,
    )

    return draft
}

/**
 * Creates a draft quoting [status].
 *
 * The quote inherits some initial values from [status], specifically:
 *
 * - [spoilerText][Status.spoilerText]
 * - [sensitive][Status.sensitive], falling back to the user's [defaultMediaSensitivity][AccountEntity.defaultMediaSensitivity]
 * - [visibility][Status.visibility]
 * - [language][Status.language], falling back to the user's [defaultPostLanguage][AccountEntity.defaultPostLanguage]
 *
 * @param status Status to quote.
 */
fun Draft.Companion.createDraftQuote(pachliAccountEntity: AccountEntity, status: Status): Draft {
    val actionable = status.actionableStatus

    val draft = Draft(
        id = 0,
        contentWarning = actionable.spoilerText,
        content = "",
        sensitive = actionable.sensitive || pachliAccountEntity.defaultMediaSensitivity,
        attachments = emptyList(),
        poll = null,
        failureMessage = null,
        visibility = actionable.visibility,
        language = actionable.language ?: pachliAccountEntity.defaultPostLanguage,
        quotePolicy = pachliAccountEntity.defaultQuotePolicy.clampToVisibility(actionable.visibility),
        scheduledAt = null,
        statusId = null,
        inReplyToId = null,
        quotedStatusId = actionable.statusId,
        cursorPosition = 0,
        state = Draft.State.DEFAULT,
    )

    return draft
}

/**
 * Creates a draft mentioning [username] (which should not have the leading `@`).
 *
 * The draft takes the default visibility from [timeline], using [createDraft],
 */
fun Draft.Companion.createDraftMention(context: Context, pachliAccountEntity: AccountEntity, timeline: Timeline, username: String): Draft {
    val content = "@$username "

    return Draft.createDraft(context, pachliAccountEntity, timeline).copy(
        content = content,
        cursorPosition = content.length,
    )
}

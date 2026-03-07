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
import app.pachli.core.model.DraftAttachment
import app.pachli.core.model.ScheduledStatus
import app.pachli.core.model.Status
import app.pachli.core.model.Timeline
import app.pachli.core.model.asQuotePolicy
import app.pachli.core.network.model.StatusSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/*

Drafts probably have a state, which controls whether or not they can be edited.

States

- DRAFTING -- user is editing the draft, it has not been sent
- SENDING -- the draft is being sent, the user can't edit it
- FAILED+error -- draft couldn't be sent

 */

@Singleton
class DraftRepository @Inject constructor(
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

    fun upsert(pachliAccountId: Long, draft: Draft) = externalScope.launch {
        draftDao.upsert(draft.asEntity(pachliAccountId))
    }

    fun deleteDraft(pachliAccountId: Long, draftId: Long) = externalScope.launch {
        draftDao.delete(pachliAccountId, draftId)
    }

    fun deleteDraftAndAttachments(pachliAccountId: Long, draftId: Long) = externalScope.launch {
        val draft = draftDao.find(draftId) ?: return@launch
        draft.attachments.forEach { attachment ->
            if (context.contentResolver.delete(attachment.uri, null, null) == 0) {
                Timber.e("Did not delete file %s", attachment.uri)
            }
        }
        deleteDraft(pachliAccountId, draft.id)
    }

    fun deleteDraftAndAttachments(pachliAccountId: Long, draft: Draft) = externalScope.launch(Dispatchers.IO) {
        draft.attachments.forEach { attachment ->
            if (context.contentResolver.delete(attachment.uri, null, null) == 0) {
                Timber.e("Did not delete file %s", attachment.uri)
            }
        }
        deleteDraft(pachliAccountId, draft.id)
    }

    fun deleteAttachments(attachments: List<DraftAttachment>) {
        attachments.forEach { attachment ->
            if (context.contentResolver.delete(attachment.uri, null, null) == 0) {
                Timber.e("Did not delete file %s", attachment.uri)
            }
        }
    }

    fun saveDraft(pachliAccountId: Long, draft: Draft): Deferred<Draft> = externalScope.async {
        val entity = draft.asEntity(pachliAccountId)
        val id = draftDao.upsert(entity)
        return@async entity.copy(id = id).asModel()
    }

    fun updateFailureState(pachliAccountId: Long, draftId: Long, failedToSend: Boolean, failedToSendNew: Boolean) = externalScope.async {
        draftDao.updateFailureState(draftId, failedToSend, failedToSendNew)
    }
}

private fun Draft.asEntity(pachliAccountId: Long) = DraftEntity(
    accountId = pachliAccountId,
    id = id,
    contentWarning = contentWarning,
    content = content,
    inReplyToId = inReplyToId,
    sensitive = sensitive,
    visibility = visibility,
    attachments = attachments,
    poll = poll,
    failedToSend = failedToSend,
    failedToSendNew = failedToSendNew,
    scheduledAt = scheduledAt,
    language = language,
    statusId = statusId,
    quotePolicy = quotePolicy,
    quotedStatusId = quotedStatusId,
)

// Note: Caller must still set attachments on ComposeOptions
fun Status.asDraft(source: StatusSource): Draft {
    val actionable = this.actionableStatus

    return Draft(
        contentWarning = source.spoilerText,
        content = source.text,
        poll = actionable.poll?.toNewPoll(createdAt),
        sensitive = actionable.sensitive,
        visibility = actionable.visibility,
        language = actionable.language,
        quotePolicy = actionable.quoteApproval.asQuotePolicy(),
        statusId = actionable.statusId,
        inReplyToId = actionable.inReplyToId,
        quotedStatusId = (actionable.quote as? Status.Quote.WithStatusId)?.statusId,
        cursorPosition = source.text.length,
    )
}

// Note: Caller must still set attachments on ComposeOptions
fun ScheduledStatus.asDraft(): Draft {
    return Draft(
        contentWarning = params.spoilerText,
        content = params.text,
        poll = params.poll,
        sensitive = params.sensitive == true,
        visibility = params.visibility,
        language = params.language,
        quotePolicy = params.quotePolicy,
        scheduledAt = scheduledAt,
        statusId = id,
        inReplyToId = params.inReplyToId,
        quotedStatusId = params.quotedStatusId,
        cursorPosition = params.text.length,
    )
}

// Note: Caller must still set attachments on ComposeOptions
fun DeletedStatus.asDraft() = Draft(
    contentWarning = spoilerText,
    content = text.orEmpty(),
    sensitive = sensitive,
    visibility = visibility,
    language = language,
    quotePolicy = quoteApproval?.asQuotePolicy() ?: AccountSource.QuotePolicy.NOBODY,
    inReplyToId = inReplyToId,
    quotedStatusId = (quote as? Status.Quote.WithStatusId)?.statusId,
)

fun Draft.Companion.createDraft(context: Context, pachliAccountEntity: AccountEntity, timeline: Timeline): Draft {
    val visibility = when (timeline) {
        Timeline.Conversations -> Status.Visibility.DIRECT
        else -> pachliAccountEntity.defaultPostPrivacy
    }

    val quotePolicy = pachliAccountEntity.defaultQuotePolicy.clampToVisibility(visibility)

    val content = when (timeline) {
        is Timeline.Hashtags -> {
            val tag = timeline.tags.first()
            getString(context, R.string.title_tag_with_initial_position).format(tag)
        }

        else -> ""
    }

    val draft = Draft(
        contentWarning = "",
        content = content,
        visibility = visibility,
        sensitive = pachliAccountEntity.defaultMediaSensitivity,
        language = pachliAccountEntity.defaultPostLanguage,
        quotePolicy = quotePolicy,
        cursorPosition = if (timeline is Timeline.Hashtags) 0 else content.length,
    )

    return draft
}

/**
 *
 * @param status Status being replied to.
 */
fun Draft.Companion.createDraftReply(pachliAccountEntity: AccountEntity, status: Status): Draft {
    val actionable = status.actionableStatus
    val account = actionable.account
    val quotePolicy = pachliAccountEntity.defaultQuotePolicy.clampToVisibility(actionable.visibility)

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
        contentWarning = actionable.spoilerText,
        content = content,
        sensitive = actionable.sensitive || pachliAccountEntity.defaultMediaSensitivity,
        visibility = actionable.visibility,
        language = actionable.language ?: pachliAccountEntity.defaultPostLanguage,
        quotePolicy = quotePolicy,
        inReplyToId = actionable.statusId,
        cursorPosition = content.length,
    )

    return draft
}

fun Draft.Companion.createDraftQuote(pachliAccountEntity: AccountEntity, status: Status): Draft {
    val actionable = status.actionableStatus

    val quotePolicy = pachliAccountEntity.defaultQuotePolicy.clampToVisibility(actionable.visibility)

    val content = ""

    val draft = Draft(
        contentWarning = actionable.spoilerText,
        content = content,
        sensitive = actionable.sensitive || pachliAccountEntity.defaultMediaSensitivity,
        visibility = actionable.visibility,
        language = actionable.language ?: pachliAccountEntity.defaultPostLanguage,
        quotePolicy = quotePolicy,
        quotedStatusId = actionable.statusId,
        cursorPosition = content.length,
    )

    return draft
}

fun Draft.Companion.createDraftMention(context: Context, pachliAccountEntity: AccountEntity, timeline: Timeline, username: String): Draft {
    val content = "@$username "

    return Draft.createDraft(context, pachliAccountEntity, timeline).copy(
        content = content,
        cursorPosition = content.length,
    )
}

/*
 * Copyright (c) 2025 Pachli Association
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

package app.pachli.core.data.extensions

import app.pachli.core.database.dao.TimelineStatusWithAccount
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.AttachmentDisplayReason
import app.pachli.core.model.FilterAction
import app.pachli.core.model.FilterContext
import app.pachli.core.model.FilterResult
import app.pachli.core.model.MatchingFilter
import app.pachli.core.model.Status

/**
 * Returns the [AttachmentDisplayAction] for [this] given the current [filterContext],
 * whether [showSensitiveMedia] is true, and the [cachedAction] (if any).
 *
 * @param filterContext Applicable filter context. May be null for timelines that are
 * not filtered (e.g., private messages).
 * @param showSensitiveMedia True if the user's preference is to show attachments
 * marked sensitive.
 * @param cachedAction
 */
fun Status.getAttachmentDisplayAction(filterContext: FilterContext?, showSensitiveMedia: Boolean, cachedAction: AttachmentDisplayAction?) = getAttachmentDisplayAction(
    filterContext,
    filtered,
    sensitive,
    showSensitiveMedia = showSensitiveMedia,
    cachedAction = cachedAction,
)

/**
 * Returns the [AttachmentDisplayAction] for [this] given the [filterContext],
 * and whether [showSensitiveMedia] is true.
 *
 * @param filterContext Applicable filter context. May be null for timelines that are
 * not filtered (e.g., private messages).
 * @param showSensitiveMedia True if the user's preference is to show attachments
 * marked sensitive.
 * @param cachedAction
 */
fun TimelineStatusWithAccount.getAttachmentDisplayAction(
    filterContext: FilterContext?,
    showSensitiveMedia: Boolean,
) = getAttachmentDisplayAction(
    filterContext,
    status.filtered,
    status.sensitive,
    showSensitiveMedia = showSensitiveMedia,
    cachedAction = viewData?.attachmentDisplayAction,
)

/**
 * Returns the [AttachmentDisplayAction] for [this] given the current [filterContext], based on
 * the [matchingFilters], whether the content is [sensitive], if [showSensitiveMedia]
 * is true, and the [cachedAction] (if any).
 *
 * @param filterContext Applicable filter context. May be null for timelines that are
 * not filtered (e.g., private messages).
 * @param matchingFilters List of filters that matched the status.
 * @param sensitive True if the status was marked senstive.
 * @param showSensitiveMedia True if the user's preference is to show attachments
 * marked sensitive.
 * @param cachedAction
 */
private fun getAttachmentDisplayAction(
    filterContext: FilterContext?,
    matchingFilters: List<FilterResult>?,
    sensitive: Boolean,
    showSensitiveMedia: Boolean,
    cachedAction: AttachmentDisplayAction?,
): AttachmentDisplayAction {
    // Hide attachments if there is any matching "blur" filter.
    val matchingBlurFilters = filterContext?.let {
        matchingFilters
            ?.filter { it.filter.filterAction == FilterAction.BLUR }
            ?.filter { it.filter.contexts.contains(filterContext) }
            ?.map { MatchingFilter(filterId = it.filter.id, title = it.filter.title) }
    }.orEmpty()

    // Any matching filters probably hides the attachment.
    if (matchingBlurFilters.isNotEmpty()) {
        val hideAction = AttachmentDisplayAction.Hide(
            reason = AttachmentDisplayReason.BlurFilter(matchingBlurFilters),
        )

        // If the cached decision is a Show then return the Show, but with an updated
        // originalDecision. This ensures that if the user then hides the attachment
        // the description that shows which filters matched reflects the user's latest
        // set of filters.
        //
        // The filters changing *doesn't* cause this to reset to Hide because the
        // user has already seen the attachment, and decided to keep seeing it.
        // Hiding it from them again isn't helpful.
        (cachedAction as? AttachmentDisplayAction.Show)?.let {
            return it.copy(originalAction = hideAction)
        }

        // Otherwise, the decision to hide is good.
        return hideAction
    }

    // Now safe to use the cached decision, if present. If the user overrode a Hide with
    // a Show this will be returned here.
    cachedAction?.let { return it }

    // Hide attachments if the status is marked sensitive and the user doesn't want to
    // see them.
    if (sensitive && !showSensitiveMedia) {
        return AttachmentDisplayAction.Hide(reason = AttachmentDisplayReason.Sensitive)
    }

    // Attachment is OK, and can be shown.
    return AttachmentDisplayAction.Show()
}

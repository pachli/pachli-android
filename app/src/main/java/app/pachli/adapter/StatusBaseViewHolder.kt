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

package app.pachli.adapter

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusItemViewData
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.ui.SetStatusContent
import app.pachli.core.ui.StatusActionListener
import app.pachli.core.ui.StatusControlView
import app.pachli.core.ui.StatusView
import com.bumptech.glide.RequestManager

abstract class StatusBaseViewHolder<T : IStatusViewData> protected constructor(
    itemView: View,
    protected val glide: RequestManager,
    protected val setStatusContent: SetStatusContent,
) : RecyclerView.ViewHolder(itemView) {
    protected val context: Context = itemView.context

    private val statusView: StatusView<T> = itemView.findViewById(R.id.status_view)

    val content: TextView = itemView.findViewById(R.id.status_content)

    protected val statusControls: StatusControlView = itemView.findViewById(R.id.status_controls)

    fun toggleContentWarning() = statusView.toggleContentWarning()

    open fun setupWithStatus(
        viewData: T,
        listener: StatusActionListener,
        statusDisplayOptions: StatusDisplayOptions,
        payloads: List<List<Any?>>?,
    ) {
        if (payloads.isNullOrEmpty()) {
            val actionable = viewData.actionable

            // Set the status
            statusView.setupWithStatus(
                setStatusContent,
                glide,
                viewData,
                listener,
                statusDisplayOptions,
            )

            // Set the controls
            statusControls.bind(
                status = actionable,
                showCounts = statusDisplayOptions.showStatsInline,
                confirmReblog = statusDisplayOptions.confirmReblogs,
                confirmFavourite = statusDisplayOptions.confirmFavourites,
                isReply = actionable.inReplyToId != null,
                isReblogged = actionable.reblogged,
                isFavourited = actionable.favourited,
                isBookmarked = actionable.bookmarked,
                replyCount = actionable.repliesCount,
                reblogCount = actionable.reblogsCount,
                favouriteCount = actionable.favouritesCount,
                onReplyClick = { listener.onReply(viewData) },
                onReblogClick = { reblog -> listener.onReblog(viewData, reblog) },
                onQuoteClick = if (statusDisplayOptions.canQuote) {
                    { listener.onQuote(viewData) }
                } else {
                    null
                },
                onFavouriteClick = { favourite -> listener.onFavourite(viewData, favourite) },
                onBookmarkClick = { bookmark -> listener.onBookmark(viewData, bookmark) },
                onMoreClick = { view -> listener.onMore(view, viewData) },
            )

            // Workaround for RecyclerView 1.0.0 / androidx.core 1.0.0
            // RecyclerView tries to set AccessibilityDelegateCompat to null
            // but ViewCompat code replaces is with the default one. RecyclerView never
            // fetches another one from its delegate because it checks that it's set so we remove it
            // and let RecyclerView ask for a new delegate.
            itemView.accessibilityDelegate = null

            itemView.contentDescription = statusView.getContentDescription(
                viewData,
                statusDisplayOptions,
            )

            itemView.setOnClickListener { listener.onViewThread(actionable) }
        } else {
            payloads.flatten().forEach { item ->
                if (item == StatusViewDataDiffCallback.Payload.STATUS_VIEW_DATA) {
                    val actionable = viewData.actionable
                    statusView.setupWithStatus(
                        setStatusContent,
                        glide,
                        viewData,
                        listener,
                        statusDisplayOptions,
                    )

                    statusControls.bind(
                        status = actionable,
                        showCounts = statusDisplayOptions.showStatsInline,
                        confirmReblog = statusDisplayOptions.confirmReblogs,
                        confirmFavourite = statusDisplayOptions.confirmFavourites,
                        isReply = actionable.inReplyToId != null,
                        isReblogged = actionable.reblogged,
                        isFavourited = actionable.favourited,
                        isBookmarked = actionable.bookmarked,
                        replyCount = actionable.repliesCount,
                        reblogCount = actionable.reblogsCount,
                        favouriteCount = actionable.favouritesCount,
                        onReplyClick = { listener.onReply(viewData) },
                        onReblogClick = { reblog -> listener.onReblog(viewData, reblog) },
                        onQuoteClick = if (statusDisplayOptions.canQuote) {
                            { listener.onQuote(viewData) }
                        } else {
                            null
                        },
                        onFavouriteClick = { favourite -> listener.onFavourite(viewData, favourite) },
                        onBookmarkClick = { bookmark -> listener.onBookmark(viewData, bookmark) },
                        onMoreClick = { view -> listener.onMore(view, viewData) },
                    )

                    itemView.contentDescription = statusView.getContentDescription(
                        viewData,
                        statusDisplayOptions,
                    )
                    return
                }
                if (item == StatusViewDataDiffCallback.Payload.CREATED) {
                    statusView.setMetaData(viewData, statusDisplayOptions, listener)
                }
                if (item == StatusViewDataDiffCallback.Payload.ATTACHMENTS) {
                    statusView.setMediaPreviews(
                        glide,
                        viewData,
                        statusDisplayOptions.mediaPreviewEnabled,
                        listener,
                        statusDisplayOptions.useBlurhash,
                    )
                }
            }
        }
    }

    open fun showStatusContent(show: Boolean) {
        itemView.visibility = if (show) View.VISIBLE else View.GONE
    }
}

/**
 * Callback to determine what, if anything, has changed in a [StatusItemViewData].
 *
 * Changes are represented by [Payload].
 */
object StatusViewDataDiffCallback : DiffUtil.ItemCallback<StatusItemViewData>() {
    /** Changes to a [StatusViewData]. */
    enum class Payload {
        /** The timestamp for the status should be recalculated and displayed. */
        CREATED,

        /**
         * The attachments have changed and should be re-displayed.
         *
         * This might be change to an attachment (e.g.,a status was edited and
         * an attachment was modified, added, or removed), or the
         * [attachmentDisplayAction][StatusViewData.attachmentDisplayAction]
         * was changed.
         */
        ATTACHMENTS,

        /**
         * The statusViewData for the status has changed, and the
         * status should be re-displayed.
         *
         * This preempts other payloads, as it typically triggers a full
         * re-display.
         */
        STATUS_VIEW_DATA,
    }

    override fun areItemsTheSame(
        oldItem: StatusItemViewData,
        newItem: StatusItemViewData,
    ): Boolean {
        return oldItem.statusId == newItem.statusId
    }

    override fun areContentsTheSame(
        oldItem: StatusItemViewData,
        newItem: StatusItemViewData,
    ): Boolean {
        return oldItem == newItem
    }

    override fun getChangePayload(
        oldItem: StatusItemViewData,
        newItem: StatusItemViewData,
    ): Any? {
        val payload = buildList {
            add(Payload.CREATED)

            // TODO: This is wrong, because statusViewData contains the status, so
            // this will trigger on every change
            if (oldItem.statusViewData != newItem.statusViewData ||
                oldItem.quotedViewData != newItem.quotedViewData
            ) {
                add(Payload.STATUS_VIEW_DATA)
                return@buildList
            }

            if (oldItem.actionable.attachments != newItem.actionable.attachments ||
                oldItem.attachmentDisplayAction != newItem.attachmentDisplayAction
            ) {
                add(Payload.ATTACHMENTS)
            }
        }

        if (payload.isEmpty()) return super.getChangePayload(oldItem, newItem)
        return payload
    }
}

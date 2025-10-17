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
        listener: StatusActionListener<T>,
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
                statusVisibility = actionable.visibility,
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
 * Callback to determine what, if anything, has changed in a [StatusViewData].
 *
 * Changes are represented by [Payload].
 */
object StatusViewDataDiffCallback : DiffUtil.ItemCallback<StatusViewData>() {
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
    }

    override fun areItemsTheSame(
        oldItem: StatusViewData,
        newItem: StatusViewData,
    ): Boolean {
        return oldItem.actionableId == newItem.actionableId
    }

    override fun areContentsTheSame(
        oldItem: StatusViewData,
        newItem: StatusViewData,
    ): Boolean {
        // Items are different always. It allows to refresh timestamp on every view holder update
        return false
    }

    override fun getChangePayload(
        oldItem: StatusViewData,
        newItem: StatusViewData,
    ): Any? {
        val payload = buildList {
            if (oldItem == newItem) {
                add(Payload.CREATED)
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

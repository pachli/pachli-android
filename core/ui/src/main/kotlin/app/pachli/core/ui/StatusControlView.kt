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

package app.pachli.core.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.util.formatNumber
import app.pachli.core.model.Status
import app.pachli.core.ui.databinding.StatusControlsBinding
import app.pachli.core.ui.extensions.expandTouchSizeToFillRow
import at.connyduck.sparkbutton.SparkButton

/**
 * User has clicked the reply control.
 *
 * @see [invoke]
 */
fun interface OnReplyClick {
    /**
     * User has clicked the reply control.
     *
     * @param view The view the user clicked on, which can be used as
     * a target to e.g., locate a pop up menu.
     */
    operator fun invoke(view: View)
}

/**
 * User has clicked the reblog control, and confirmed this choice if
 * necessary.
 *
 * @see [invoke]
 */
fun interface OnReblogClick {
    /**
     * User has clicked the reblog control, and confirmed this choice if
     * necessary.
     *
     * @param reblog The user's intention. True if they want to reblog the
     * status, false otherwise. This is the **opposite** of the button's state
     * at the time the user clicked.
     */
    operator fun invoke(reblog: Boolean)
}

/**
 * User has clicked to quote the status.
 *
 * @see [invoke]
 */
fun interface OnQuoteClick {
    /**
     * User has clicked to quote the status.
     */
    operator fun invoke()
}

/**
 * User has clicked the favourite control, and confirmed this choice
 * if necessary.
 *
 * @see [invoke]
 */
fun interface OnFavouriteClick {
    /**
     * User has clicked the favourite control, and confirmed this choice
     * if necessary.
     *
     * @param favourite The user's intention. True if they want to
     * favourite the status, false otherwise. This is the **opposite**
     * of the button's state at the time the user clicked.
     */
    operator fun invoke(favourite: Boolean)
}

/**
 * User has clicked to bookmark the status.
 *
 * @see [invoke]
 */
fun interface OnBookmarkClick {
    /**
     * User has clicked the bookmark control.
     *
     * @param bookmark The user's intention. True if they want to
     * bookmark the status, false otherwise. This is the **opposite**
     * of the button's state at the time the user clicked.
     */
    operator fun invoke(bookmark: Boolean)
}

/**
 * User has clicked the "..." (more) control.
 *
 * @see [invoke]
 */
fun interface OnMoreClick {
    /**
     * User has clicked the "..." (more) control.
     *
     * @param view The view the user clicked on, which can be used as
     * a target to e.g., locate a pop up menu.
     */
    operator fun invoke(view: View)
}

/**
 * Shows a row of controls allowing the user to interact with a status.
 *
 * From start to end the typical controls are:
 *
 * - Reply
 * - Reblog
 * - Favourite
 * - Bookmark
 * - More
 *
 * Data is bound to the control using [bind], see that method's documentation
 * for more details.
 *
 * @property actions
 */
class StatusControlView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {
    private val binding = StatusControlsBinding.inflate(LayoutInflater.from(context), this)

    private var showCounts: Boolean = false

    private var replyCount: Int = 0
        set(value) {
            if (showCounts) {
                binding.replyCount.text = formatNumber(value.toLong(), 1000)
            } else {
                binding.replyCount.text = when (value) {
                    0 -> ""
                    1 -> "1"
                    else -> context.getString(R.string.status_count_one_plus)
                }
            }

            field = value
        }

    private var reblogCount: Int = 0
        set(value) {
            binding.reblogCount.text = formatNumber(value.toLong(), 1000)
            field = value
        }

    private var favouriteCount: Int = 0
        set(value) {
            binding.favouriteCount.text = formatNumber(value.toLong(), 1000)
            field = value
        }

    private var _actions = mutableSetOf<Int>()

    /** Set of accessibility actions this view supports. */
    val actions: Set<Int>
        get() = _actions

    init {
        expandTouchSizeToFillRow(
            listOf(
                binding.reply,
                binding.reblog,
                binding.favourite,
                binding.bookmark,
                binding.statusMore,
            ),
        )
    }

    /**
     * Binds data to the control and configures click handlers.
     *
     * The parameters control the appearance of the views.
     *
     * @param status Actionable status being controlled.
     * @param showCounts True if interaction counts should be shown on the
     * controls (number of replies, favourites, etc). If false numbers are
     * either hidden, or in the case of replies, truncated.
     * @param confirmReblog True if reblog clicks should show a confirmation
     * before calling [onReblogClick].
     * @param confirmFavourite True if favourite clicks should show a
     * confirmation before calling [onFavouriteClick].
     * @param isReply True if the status is a reply. The "reply" button will
     * use a different drawable to indicate the status is a reply.
     * @param isReblogged True if the user has reblogged the status.
     * @param isFavourited True if the user has favourited the status.
     * @param isBookmarked True if the user has bookmarked the status.
     * @param replyCount Count of replies to show. Truncated if [showCounts]
     * is false.
     * @param reblogCount Count of reblogs to show. Ignored if [showCounts]
     * is false.
     * @param favouriteCount Count of favourites to show. Ignored if
     * [showCounts] is false.
     * @param onReplyClick Called when the user wants to reply to the status.
     * @param onReblogClick Called when the user wants to reblog the status. If
     * null reblogging is disabled. If [confirmReblog] is true the user will
     * have confirmed *before* this is called.
     * @param onQuoteClick Called when the user wants to quote the status. If
     * null quoting is disabled.
     * @param onFavouriteClick Called when the user wants to favourite the
     * status. If [confirmFavourite] is true the user will have confirmed
     * *before* this is called.
     * @param onBookmarkClick Called when the user wants to bookmark the status.
     * @param onMoreClick Called when the user clicks the "..." more button.
     */
    fun bind(
        status: Status,
        showCounts: Boolean,
        confirmReblog: Boolean,
        confirmFavourite: Boolean,
        isReply: Boolean,
        isReblogged: Boolean,
        isFavourited: Boolean,
        isBookmarked: Boolean,
        replyCount: Int,
        reblogCount: Int,
        favouriteCount: Int,
        onReplyClick: OnReplyClick,
        onReblogClick: OnReblogClick? = null,
        onQuoteClick: OnQuoteClick? = null,
        onFavouriteClick: OnFavouriteClick,
        onBookmarkClick: OnBookmarkClick,
        onMoreClick: OnMoreClick? = null,
    ) {
        this.showCounts = showCounts
        this.replyCount = replyCount
        this.reblogCount = reblogCount
        this.favouriteCount = favouriteCount

        _actions.clear()

        // Replies
        bindReply(isReply, onReplyClick)

        // Reblogs. Not every status allows reblogs (e.g., direct messages). On those
        // onReblogClick will be null. They may still be quotable, so onQuoteClick may
        // not be null.
        bindReblog(status, showCounts, confirmReblog, isReblogged, onReblogClick, onQuoteClick)

        // Favourite
        bindFavourite(showCounts, confirmFavourite, isFavourited, onFavouriteClick)

        // Bookmark
        bindBookmark(isBookmarked, onBookmarkClick)

        // More
        onMoreClick?.let {
            _actions.add(R.id.action_more)
            binding.statusMore.setOnClickListener { onMoreClick(it) }
        }
    }

    /**
     * Sets the control content and click listener.
     *
     * @param isReply True if the status is a reply.
     * @param onReplyClick
     */
    private fun bindReply(isReply: Boolean, onReplyClick: OnReplyClick) {
        _actions.add(R.id.action_reply)
        binding.reply.setImageResource(if (isReply) R.drawable.ic_reply_all_24dp else R.drawable.ic_reply_24dp)
        binding.reply.setOnClickListener { onReplyClick(it) }
    }

    /**
     * Sets the control content and click listener.
     *
     * @param status Actionable status
     * @param showCounts True if interaction counts should be shown.
     * @param isReblogged True if the user has reblogged the status.
     * @param confirmReblog True if the user should be prompted to confirm
     * the click.
     * @param onReblogClick Called when the user wants to reblog the status. If
     * null reblogging is disabled.
     * @param onQuoteClick Called when the user wants to quote the status. If
     * null quoting is disabled.
     */
    private fun bindReblog(
        status: Status,
        showCounts: Boolean,
        confirmReblog: Boolean,
        isReblogged: Boolean,
        onReblogClick: OnReblogClick?,
        onQuoteClick: OnQuoteClick?,
    ) {
        val canReblog = onReblogClick != null && status.visibility.allowsReblog
        val canQuote = onQuoteClick != null && status.visibility.allowsQuote

        val statusVisibility = status.visibility

        if (canReblog) _actions.add(R.id.action_reblog)
        // TODO: Quote accessibility action here

        binding.reblogCount.visible(showCounts && canReblog)

        binding.reblog.show()
        binding.reblog.isChecked = isReblogged

        val eventListener = { _: SparkButton, checked: Boolean ->
            val reblog = !checked
            if (confirmReblog || canQuote) {
                showReblogMenu(status, reblog, onReblogClick, onQuoteClick)
                false
            } else {
                onReblogClick?.invoke(reblog)
                true
            }
        }

        if (canReblog || canQuote) {
            binding.reblog.setEventListener(eventListener)
            binding.reblog.setOnLongClickListener { view ->
                val reblog = !(view as SparkButton).isChecked
                showReblogMenu(status, reblog, onReblogClick, onQuoteClick)
                true
            }
            binding.reblog.isLongClickable = true
            binding.reblog.isEnabled = true
        } else {
            binding.reblog.setEventListener(null)
            binding.reblog.setOnLongClickListener(null)
            binding.reblog.isEnabled = false
        }

        // Decide which icons to show on the increasingly misnamed "reblog" button
        val (resActive, resInactive) = when {
            // Reblogging has preference. If this can be reblogged then use those icons.
            canReblog -> (R.drawable.ic_reblog_active_24dp to R.drawable.ic_reblog_24dp)

            // If it can be quoted then use the quote icon (which doesn't have active/inactive
            // variants).
            canQuote -> (R.drawable.format_quote_24px to R.drawable.format_quote_24px)

            // Otherwise, use the relevant "can't be reblogged" icons depending on the
            // visibility.
            statusVisibility == Status.Visibility.UNKNOWN ||
                statusVisibility == Status.Visibility.PRIVATE -> (R.drawable.ic_reblog_private_active_24dp to R.drawable.ic_reblog_private_24dp)

            else -> (R.drawable.ic_reblog_direct_24dp to R.drawable.ic_reblog_direct_24dp)
        }

        binding.reblog.setActiveImage(resActive)
        binding.reblog.setInactiveImage(resInactive)
    }

    /**
     * Shows a popup menu for reblog-like actions to perform on the status.
     *
     * @param status Actionable status
     * @param reblog The user's intention. True if they want to reblog the
     * status, false otherwise. This is the **opposite** of the button's
     * state at the time the user clicked.
     * @param onReblogClick Called if the user wants to reblog the status.
     * @param onQuoteClick Called if the user wants to quote the status.
     */
    private fun showReblogMenu(status: Status, reblog: Boolean, onReblogClick: OnReblogClick?, onQuoteClick: OnQuoteClick?) {
        val (canQuote, quoteLabel) = when (status.quoteApproval.currentUser) {
            Status.QuoteApproval.QuoteApprovalCurrentUser.UNKNOWN -> Pair(false, R.string.label_author_denied_quote)
            Status.QuoteApproval.QuoteApprovalCurrentUser.AUTOMATIC -> Pair(true, R.string.action_quote)
            Status.QuoteApproval.QuoteApprovalCurrentUser.MANUAL -> Pair(true, R.string.action_request_quote)
            Status.QuoteApproval.QuoteApprovalCurrentUser.DENIED -> Pair(false, R.string.label_author_denied_quote)
        }

        val canReblog = status.visibility.allowsReblog

        PopupMenu(context, binding.reblog).apply {
            inflate(R.menu.status_reblog)
            menu.findItem(R.id.menu_action_quote).apply {
                isVisible = onQuoteClick != null
                isEnabled = canQuote
                setTitle(quoteLabel)
            }
            menu.findItem(R.id.menu_action_reblog).isVisible = reblog && canReblog && onReblogClick != null
            menu.findItem(R.id.menu_action_unreblog).isVisible = !reblog && canReblog && onReblogClick != null
            setOnMenuItemClickListener { item ->
                return@setOnMenuItemClickListener when (item.itemId) {
                    R.id.menu_action_quote -> {
                        onQuoteClick?.invoke()
                        true
                    }

                    R.id.menu_action_reblog,
                    R.id.menu_action_unreblog,
                    -> {
                        binding.reblog.playAnimation()
                        onReblogClick?.invoke(reblog)
                        true
                    }

                    else -> false
                }
            }
        }.show()
    }

    /**
     * Sets the control content and click listener.
     *
     * @param showCounts True if interaction counts should be shown.
     * @param confirmFavourite True if the user should be prompted to confirm
     * the click.
     * @param isFavourited True if the user has favourited the status.
     * @param onFavouriteClick Called when the user wants to favourite this
     * status.
     */
    private fun bindFavourite(
        showCounts: Boolean,
        confirmFavourite: Boolean,
        isFavourited: Boolean,
        onFavouriteClick: OnFavouriteClick,
    ) {
        _actions.add(R.id.action_favourite)
        binding.favouriteCount.visible(showCounts)
        binding.favourite.isChecked = isFavourited

        binding.favourite.setEventListener { _, checked ->
            val favourite = !checked
            if (confirmFavourite) {
                showConfirmFavourite(favourite, onFavouriteClick)
                false
            } else {
                onFavouriteClick(favourite)
                true
            }
        }
    }

    /**
     * Shows a popup menu for the user to confirm they want to (un)favourite
     * the status.
     *
     * @param favourite The user's intention. True if they want to
     * favourite the status, false otherwise. This is the **opposite**
     * of the button's state at the time the user clicked.
     * @param onFavouriteClick Called if the user confirms.
     */
    private fun showConfirmFavourite(favourite: Boolean, onFavouriteClick: OnFavouriteClick) {
        PopupMenu(context, binding.favourite).apply {
            inflate(R.menu.status_favourite)
            menu.findItem(R.id.menu_action_favourite).isVisible = favourite
            menu.findItem(R.id.menu_action_unfavourite).isVisible = !favourite
            setOnMenuItemClickListener {
                binding.favourite.playAnimation()
                onFavouriteClick(favourite)
                true
            }
        }.show()
    }

    /**
     * Sets the control content and click listener.
     *
     * @param isBookmarked True if the user has bookmarked this status.
     * @param onBookmarkClick Called when the user wants to bookmark this
     * status.
     */
    private fun bindBookmark(isBookmarked: Boolean, onBookmarkClick: OnBookmarkClick) {
        _actions.add(R.id.action_bookmark)
        binding.bookmark.isChecked = isBookmarked
        binding.bookmark.setEventListener { _, checked ->
            val bookmark = !checked
            onBookmarkClick(bookmark)
            true
        }
    }
}

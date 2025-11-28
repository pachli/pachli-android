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

package app.pachli.adapter

import android.view.View
import androidx.core.text.HtmlCompat
import androidx.core.util.TypedValueCompat.dpToPx
import app.pachli.R
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.model.FilterAction
import app.pachli.core.model.TimelineAccount
import app.pachli.core.ui.SetStatusContent
import app.pachli.core.ui.StatusActionListener
import app.pachli.core.ui.emojify
import app.pachli.databinding.ItemStatusBinding
import com.bumptech.glide.RequestManager

open class StatusViewHolder<T : IStatusViewData>(
    private val binding: ItemStatusBinding,
    glide: RequestManager,
    setStatusContent: SetStatusContent,
    root: View? = null,
) : StatusBaseViewHolder<T>(root ?: binding.root, glide, setStatusContent) {

    override fun setupWithStatus(
        viewData: T,
        listener: StatusActionListener,
        statusDisplayOptions: StatusDisplayOptions,
        payloads: List<List<Any?>>?,
    ) {
        with(binding) {
            if (payloads.isNullOrEmpty()) {
                setStatusInfo(viewData, statusDisplayOptions, listener)
            }
            super.setupWithStatus(viewData, listener, statusDisplayOptions, payloads)
        }
    }

    /**
     * Sets the (optional) content of the "status info" text that appears
     * above the status.
     */
    private fun ItemStatusBinding.setStatusInfo(
        viewData: T,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener,
    ) {
        if (!statusDisplayOptions.showStatusInfo || viewData.contentFilterAction == FilterAction.WARN) {
            statusInfo.hide()
            return
        }

        val status = viewData.actionable

        viewData.rebloggingStatus?.let { reblogging ->
            setStatusInfoAsReblog(viewData, reblogging.account, statusDisplayOptions, listener)
            return
        }

        if (status.inReplyToAccountId != null) {
            setStatusInfoAsReply(viewData, statusDisplayOptions)
            return
        }

        statusInfo.hide()
    }

    /**
     * Sets [statusInfo] assuming [viewData] contains a status that is a reply
     * to another status.
     *
     * @param viewData
     * @param statusDisplayOptions
     */
    private fun ItemStatusBinding.setStatusInfoAsReply(
        viewData: T,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        // Belt and braces. Caller should have checked this, but just in case.
        val status = viewData.actionable
        if (status.inReplyToAccountId == null) {
            statusInfo.hide()
            return
        }

        statusInfo.text = when {
            // Self-replies are marked as such.
            status.isSelfReply -> context.getString(app.pachli.core.ui.R.string.post_continued_thread)

            // If we have the account info we can show the name.
            viewData.replyToAccount != null -> {
                HtmlCompat.fromHtml(
                    context.getString(
                        app.pachli.core.ui.R.string.post_replied_to_fmt,
                        viewData.replyToAccount?.name.unicodeWrap(),
                    ),
                    HtmlCompat.FROM_HTML_MODE_LEGACY,
                ).emojify(
                    glide,
                    viewData.replyToAccount?.emojis,
                    statusInfo,
                    statusDisplayOptions.animateEmojis,
                )
            }

            // Otherwise a generic "Replied" message.
            else -> context.getString(app.pachli.core.ui.R.string.post_replied)
        }

        statusInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(app.pachli.core.ui.R.drawable.ic_reply_18dp, 0, 0, 0)
        statusInfo.isClickable = false
        statusInfo.show()
    }

    /**
     * @param viewData
     * @param rebloggingAccount The account doing the reblogging (not the account
     * being reblogged).
     * @param statusDisplayOptions
     * @param listener
     */
    private fun ItemStatusBinding.setStatusInfoAsReblog(
        viewData: T,
        rebloggingAccount: TimelineAccount,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener,
    ) {
        statusInfo.text = HtmlCompat.fromHtml(
            context.getString(
                app.pachli.core.ui.R.string.post_boosted_fmt,
                rebloggingAccount.name.unicodeWrap(),
            ),
            HtmlCompat.FROM_HTML_MODE_LEGACY,
        ).emojify(
            glide,
            rebloggingAccount.emojis,
            statusInfo,
            statusDisplayOptions.animateEmojis,
        )

        statusInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_reblog_18dp, 0, 0, 0)
        statusInfo.setOnClickListener { listener.onOpenReblog(viewData.status) }
        statusInfo.show()
    }

    // don't use this on the same ViewHolder as setRebloggedByDisplayName, will cause recycling issues as paddings are changed
    protected fun setPollInfo(ownPoll: Boolean) = with(binding) {
        statusInfo.setText(if (ownPoll) R.string.poll_ended_created else R.string.poll_ended_voted)
        statusInfo.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_poll_24dp, 0, 0, 0)
        statusInfo.compoundDrawablePadding = dpToPx(10f, context.resources.displayMetrics).toInt()
        statusInfo.setPaddingRelative(dpToPx(28f, context.resources.displayMetrics).toInt(), 0, 0, 0)
        statusInfo.show()
    }

    protected fun hideStatusInfo() = with(binding) {
        statusInfo.hide()
    }
}

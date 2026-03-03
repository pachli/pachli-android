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
import android.widget.TextView
import androidx.core.text.HtmlCompat
import app.pachli.R
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.model.FilterAction
import app.pachli.core.model.TimelineAccount
import app.pachli.core.ui.SetContent
import app.pachli.core.ui.StatusActionListener
import app.pachli.core.ui.emojify
import app.pachli.databinding.ItemStatusBinding
import com.bumptech.glide.RequestManager

open class StatusViewHolder<T : IStatusViewData>(
    private val binding: ItemStatusBinding,
    glide: RequestManager,
    setContent: SetContent,
    root: View? = null,
) : StatusBaseViewHolder<T>(root ?: binding.root, glide, setContent) {

    override fun setupWithStatus(
        viewData: T,
        listener: StatusActionListener,
        statusDisplayOptions: StatusDisplayOptions,
        payloads: List<List<Any?>>?,
    ) {
        if (payloads.isNullOrEmpty()) {
            setStatusInfo(binding.statusInfo, viewData, statusDisplayOptions, listener)
        }
        super.setupWithStatus(viewData, listener, statusDisplayOptions, payloads)
    }

    /**
     * Sets the (optional) content of the "status info" text that appears
     * above the status.
     *
     * @param statusInfo
     * @param viewData
     * @param statusDisplayOptions
     * @param listener
     */
    protected open fun setStatusInfo(
        statusInfo: TextView,
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
            setStatusInfoAsReblog(statusInfo, viewData, reblogging.account, statusDisplayOptions, listener)
            return
        }

        if (status.inReplyToAccountId != null) {
            setStatusInfoAsReply(statusInfo, viewData, statusDisplayOptions)
            return
        }

        statusInfo.hide()
    }

    /**
     * Sets [statusInfo] assuming [viewData] contains a status that is a reply
     * to another status.
     *
     * @param statusInfo [TextView] to modify.
     * @param viewData
     * @param statusDisplayOptions
     */
    private fun setStatusInfoAsReply(
        statusInfo: TextView,
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
    private fun setStatusInfoAsReblog(
        statusInfo: TextView,
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

    protected fun hideStatusInfo() = with(binding) {
        statusInfo.hide()
    }
}

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

import android.text.InputFilter
import android.view.View
import androidx.core.util.TypedValueCompat.dpToPx
import app.pachli.R
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.common.util.SmartLengthInputFilter
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.model.Emoji
import app.pachli.core.model.FilterAction
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
        listener: StatusActionListener<T>,
        statusDisplayOptions: StatusDisplayOptions,
        payloads: List<List<Any?>>?,
    ) {
        with(binding) {
            if (payloads.isNullOrEmpty()) {
                val reblogging = viewData.rebloggingStatus
                if (reblogging == null || viewData.contentFilterAction === FilterAction.WARN) {
                    statusInfo.hide()
                } else {
                    val rebloggedByDisplayName = reblogging.account.name
                    setRebloggedByDisplayName(
                        rebloggedByDisplayName,
                        reblogging.account.emojis,
                        statusDisplayOptions,
                    )
                    statusInfo.setOnClickListener {
                        listener.onOpenReblog(viewData.status)
                    }
                }
            }
            super.setupWithStatus(viewData, listener, statusDisplayOptions, payloads)
        }
    }

    private fun setRebloggedByDisplayName(
        name: CharSequence,
        accountEmoji: List<Emoji>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        with(binding) {
            val wrappedName: CharSequence = name.unicodeWrap()
            val boostedText: CharSequence = context.getString(app.pachli.core.ui.R.string.post_boosted_format, wrappedName)
            val emojifiedText =
                boostedText.emojify(glide, accountEmoji, statusInfo, statusDisplayOptions.animateEmojis)
            statusInfo.text = emojifiedText
            statusInfo.show()
        }
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

    companion object {
        private val COLLAPSE_INPUT_FILTER = arrayOf<InputFilter>(SmartLengthInputFilter)
        private val NO_INPUT_FILTER = arrayOfNulls<InputFilter>(0)
    }
}

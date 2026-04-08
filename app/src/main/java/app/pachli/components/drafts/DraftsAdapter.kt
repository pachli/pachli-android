/* Copyright 2021 Tusky Contributors
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

package app.pachli.components.drafts

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Checkable
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.visible
import app.pachli.core.model.Draft
import app.pachli.databinding.ItemDraftBinding
import com.bumptech.glide.RequestManager

interface DraftActionListener {
    /** User has tapped on [draft]. */
    fun onOpenDraft(draft: Draft)

    /** @return True if [draft] is checked. */
    fun isDraftChecked(draft: Draft): Boolean

    /** Set the checked state of [draft] to [isChecked]. */
    fun setDraftChecked(draft: Draft, isChecked: Boolean)
}

class DraftsAdapter(
    private val glide: RequestManager,
    private val listener: DraftActionListener,
) : PagingDataAdapter<DraftViewData, DraftViewHolder>(
    object : DiffUtil.ItemCallback<DraftViewData>() {
        override fun areItemsTheSame(oldItem: DraftViewData, newItem: DraftViewData): Boolean {
            return oldItem.draft.id == newItem.draft.id
        }

        override fun areContentsTheSame(oldItem: DraftViewData, newItem: DraftViewData): Boolean {
            return oldItem == newItem
        }
    },
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DraftViewHolder {
        val binding = ItemDraftBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return DraftViewHolder(binding, glide, listener)
    }

    override fun onBindViewHolder(holder: DraftViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }
}

class DraftViewHolder(
    private val binding: ItemDraftBinding,
    glide: RequestManager,
    private val listener: DraftActionListener,
) : RecyclerView.ViewHolder(binding.root), Checkable {
    private var draft: Draft? = null

    init {
        with(binding) {
            draftMediaPreview.layoutManager = LinearLayoutManager(binding.root.context, RecyclerView.HORIZONTAL, false)
            draftMediaPreview.adapter = DraftMediaAdapter(glide) {
                draft?.let { listener.onOpenDraft(it) }
            }

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                draft?.let { listener.setDraftChecked(it, isChecked) }
            }

            root.setOnClickListener {
                draft?.let { listener.onOpenDraft(it) }
            }
        }
    }

    fun bind(draftViewData: DraftViewData) = with(binding) {
        val draft = draftViewData.draft

        this@DraftViewHolder.draft = draftViewData.draft

        isChecked = draftViewData.isChecked

        checkBox.isVisible = draft.state == Draft.State.DEFAULT
        sendingIndicator.isVisible = draft.state == Draft.State.SENDING
        editButton.isVisible = draft.state == Draft.State.EDITING

        contentWarning.visible(!draft.contentWarning.isNullOrEmpty())
        contentWarning.text = draft.contentWarning
        content.text = draft.content

        draftMediaPreview.visible(draft.attachments.isNotEmpty())
        (draftMediaPreview.adapter as DraftMediaAdapter).submitList(draft.attachments)

        draft.poll?.apply {
            draftPoll.show()
            draftPoll.setPoll(this)
        } ?: draftPoll.hide()

        if (draft.failureMessage.isNullOrEmpty()) {
            dividerFailure.hide()
            draftFailure.hide()
        } else {
            dividerFailure.show()
            draftFailure.show()
            draftFailure.text = binding.root.context.getString(
                R.string.send_post_failure_message_prefix,
                draft.failureMessage,
            )
        }
    }

    override fun isChecked() = binding.checkBox.isChecked

    override fun setChecked(checked: Boolean) {
        binding.checkBox.isChecked = checked
    }

    override fun toggle() {
        isChecked = !binding.checkBox.isChecked
    }
}

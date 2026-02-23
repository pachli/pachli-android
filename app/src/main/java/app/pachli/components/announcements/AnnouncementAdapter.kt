/* Copyright 2020 Tusky Contributors
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

package app.pachli.components.announcements

import android.graphics.drawable.Drawable
import android.os.Build
import android.text.SpannableString
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.size
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.util.AbsoluteTimeFormatter
import app.pachli.core.model.Announcement
import app.pachli.core.ui.BindingHolder
import app.pachli.core.ui.EmojiSpan
import app.pachli.core.ui.LinkListener
import app.pachli.core.ui.SetContent
import app.pachli.core.ui.clearEmojiTargets
import app.pachli.core.ui.getRelativeTimeSpanString
import app.pachli.core.ui.setEmojiTargets
import app.pachli.databinding.ItemAnnouncementBinding
import app.pachli.util.equalByMinute
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.target.Target
import com.google.android.material.chip.Chip
import kotlin.collections.emptyList

interface AnnouncementActionListener : LinkListener {
    fun openReactionPicker(announcementId: String, target: View)
    fun addReaction(announcementId: String, name: String)
    fun removeReaction(announcementId: String, name: String)
}

class AnnouncementAdapter(
    private val glide: RequestManager,
    private val setContent: SetContent,
    private var items: List<Announcement> = emptyList(),
    private val listener: AnnouncementActionListener,
    private val hideStatsInDetailedPosts: Boolean = false,
    private val animateEmojis: Boolean = false,
    private val useAbsoluteTime: Boolean = false,
) : RecyclerView.Adapter<BindingHolder<ItemAnnouncementBinding>>() {
    private val absoluteTimeFormatter = AbsoluteTimeFormatter()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemAnnouncementBinding> {
        val binding = ItemAnnouncementBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemAnnouncementBinding>, position: Int) {
        val item = items[position]
        val now = System.currentTimeMillis()

        val publishTimeToDisplay = if (useAbsoluteTime) {
            absoluteTimeFormatter.format(item.publishedAt, shortFormat = item.allDay)
        } else {
            getRelativeTimeSpanString(holder.binding.root.context, item.publishedAt.time, now)
        }

        val updatedAtText = if (item.updatedAt.equalByMinute(item.publishedAt)) {
            // they're the same, don't show the "updated" indicator
            ""
        } else {
            // they're a minute or more apart, show the "updated" indicator
            val formattedUpdatedAt = if (useAbsoluteTime) {
                absoluteTimeFormatter.format(item.updatedAt, item.allDay)
            } else {
                getRelativeTimeSpanString(holder.binding.root.context, item.updatedAt.time, now)
            }
            holder.binding.root.context.getString(R.string.announcement_date_updated, formattedUpdatedAt)
        }

        val announcementDate = holder.binding.root.context.getString(R.string.announcement_date, publishTimeToDisplay, updatedAtText)
        holder.binding.announcementDate.text = announcementDate

        val text = holder.binding.text
        val chips = holder.binding.chipGroup
        val addReactionChip = holder.binding.addReactionChip

        setContent(
            glide = glide,
            textView = text,
            content = item.content,
            emojis = item.emojis,
            animateEmojis = animateEmojis,
            removeQuoteInline = false,
            linkListener = listener,
        )

        // If wellbeing mode is enabled, announcement badge counts should not be shown.
        if (hideStatsInDetailedPosts) {
            // Since reactions are not visible in wellbeing mode,
            // we shouldn't be able to add any ourselves.
            addReactionChip.visibility = View.GONE
            return
        }

        // hide button if announcement badge limit is already reached
        addReactionChip.visible(item.reactions.size < 8)

        chips.clearEmojiTargets(glide)
        val targets = ArrayList<Target<Drawable>>(item.reactions.size)

        item.reactions.forEachIndexed { i, reaction ->
            (
                chips.getChildAt(i)?.takeUnless { it.id == R.id.addReactionChip } as Chip?
                    ?: Chip(ContextThemeWrapper(chips.context, com.google.android.material.R.style.Widget_MaterialComponents_Chip_Choice)).apply {
                        isCheckable = true
                        checkedIcon = null
                        chips.addView(this, i)
                    }
                )
                .apply {
                    if (reaction.url == null) {
                        val reactionNameAndCountText = holder.binding.root.context.getString(R.string.reaction_name_and_count, reaction.name, reaction.count)
                        this.text = reactionNameAndCountText
                    } else {
                        // we set the EmojiSpan on a space, because otherwise the Chip won't have the right size
                        // https://github.com/tuskyapp/Tusky/issues/2308
                        val spannable = SpannableString("  ${reaction.count}")
                        val span = EmojiSpan(this)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            span.contentDescription = reaction.name
                        }
                        val target = span.createGlideTarget(this, animateEmojis)
                        spannable.setSpan(span, 0, 1, 0)
                        glide.asDrawable()
                            .load(
                                if (animateEmojis) {
                                    reaction.url
                                } else {
                                    reaction.staticUrl
                                },
                            )
                            .into(target)
                        targets.add(target)
                        this.text = spannable
                    }

                    isChecked = reaction.me

                    setOnClickListener {
                        if (reaction.me) {
                            listener.removeReaction(item.id, reaction.name)
                        } else {
                            listener.addReaction(item.id, reaction.name)
                        }
                    }
                }
        }

        while (chips.size - 1 > item.reactions.size) {
            chips.removeViewAt(item.reactions.size)
        }

        // Store Glide targets for later cancellation
        chips.setEmojiTargets(targets)

        addReactionChip.setOnClickListener {
            listener.openReactionPicker(item.id, it)
        }
    }

    override fun onViewRecycled(holder: BindingHolder<ItemAnnouncementBinding>) {
        holder.binding.chipGroup.clearEmojiTargets(glide)
    }

    override fun getItemCount() = items.size

    fun updateList(items: List<Announcement>) {
        this.items = items
        notifyDataSetChanged()
    }
}

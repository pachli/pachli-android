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
import android.text.InputFilter
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.HtmlCompat
import androidx.core.util.TypedValueCompat.dpToPx
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.common.util.AbsoluteTimeFormatter
import app.pachli.core.common.util.SmartLengthInputFilter
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.database.model.TranslationState
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.Emoji
import app.pachli.core.model.PreviewCardKind
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.preferences.CardViewMode
import app.pachli.core.ui.PollViewData.Companion.from
import app.pachli.core.ui.extensions.contentDescription
import app.pachli.core.ui.extensions.description
import app.pachli.core.ui.extensions.getContentDescription
import app.pachli.core.ui.extensions.getFormattedDescription
import com.bumptech.glide.RequestManager
import com.google.android.material.color.MaterialColors
import java.text.NumberFormat
import java.util.Date

/** Array of [InputFilter] to collapse status content. */
private val COLLAPSE_INPUT_FILTER = arrayOf<InputFilter>(SmartLengthInputFilter)

/** Empty array of [InputFilter] to uncollapse status content. */
private val NO_INPUT_FILTER = emptyArray<InputFilter>()

/** Formatter for numbers (e.g., reblog counts). */
private val NUMBER_FORMATTER = NumberFormat.getNumberInstance()

/** Formatter for times (e.g., status creation or edit time). */
private val TIME_FORMATTER = AbsoluteTimeFormatter()

/**
 * Compound view and default behaviour for all classes that show a
 * [app.pachli.core.model.Status].
 *
 * Derived classes should:
 *
 * - Inflate the specific layout for the view.
 * - Assign their views to the abstract properties in this class
 * - Override behaviour as appropriate.
 *
 * @param T The specific type of data this view will be showing.
 */
abstract class StatusView<T : IStatusViewData> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    /** View displaying the status account's avatar. */
    abstract val avatar: ImageView

    /**
     * View displaying a smaller image inset in to [avatar].
     *
     * E.g., an icon indicating the account is a bot, or an avatar of an
     * account related to the status.
     */
    abstract val avatarInset: ImageView

    /** View displaying the status account's role chips. */
    abstract val roleChipGroup: RoleChipGroup

    /** View displaying the status account's name. */
    abstract val displayName: TextView

    /** View displaying the status account's handle. */
    abstract val username: TextView

    /**
     * View displaying additional meta-information about the status.
     *
     * Typically:
     * - When the status was posted.
     * - An indicator if the status was edited.
     */
    abstract val metaInfo: TextView

    /** View displaying the status' content warning, if present. */
    abstract val contentWarningDescription: TextView

    /**
     * Button to toggle displaying the status' content if it has a content
     * warning.
     */
    abstract val contentWarningButton: Button

    /** View displaying the status' content. */
    abstract val content: TextView

    /**
     * Button to toggle expanding/collapsing the status' content.
     *
     * May be null if the status display does not support collapsible
     * content. E.g., [DetailedStatusView].
     */
    abstract val buttonToggleContent: Button?

    /**
     * [AttachmentsView] that encompasses and lays out all the attachment
     * previews.
     */
    abstract val attachmentsView: AttachmentsView

    /** [PollView] displaying the status' poll, if present. */
    abstract val pollView: PollView

    /** [PreviewCardView] displaying the status' preview card, if present. */
    abstract val cardView: PreviewCardView

    /** View displaying the provider of the status' translation. */
    abstract val translationProvider: TextView

    protected val avatarRadius48dp = context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_48dp)
    private val avatarRadius36dp = context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_36dp)
    private val avatarRadius24dp = context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_24dp)

    fun setDisplayName(
        glide: RequestManager,
        name: String,
        customEmojis: List<Emoji>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        displayName.text = name.emojify(glide, customEmojis, displayName, statusDisplayOptions.animateEmojis)
    }

    fun setUsername(name: String) {
        username.text = context.getString(DR.string.post_username_format, name)
    }

    fun toggleContentWarning() {
        contentWarningButton.performClick()
    }

    fun setSpoilerAndContent(
        setStatusContent: SetStatusContent,
        glide: RequestManager,
        viewData: T,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener<T>,
    ) {
        val spoilerText = when (viewData.translationState) {
            TranslationState.SHOW_ORIGINAL -> viewData.actionable.spoilerText
            TranslationState.TRANSLATING -> viewData.actionable.spoilerText
            TranslationState.SHOW_TRANSLATION -> viewData.translation?.spoilerText.orEmpty()
        }

        // Determine sensitive state from the original spoiler text, not the translated
        // text, in case the translation erroneously returns empty spoiler text.
        val sensitive = !TextUtils.isEmpty(viewData.actionable.spoilerText)
        val expanded = viewData.isExpanded
        if (sensitive) {
            val emojiSpoiler = spoilerText.emojify(
                glide,
                viewData.actionable.emojis,
                contentWarningDescription,
                statusDisplayOptions.animateEmojis,
            )
            contentWarningDescription.text = emojiSpoiler
            contentWarningDescription.visibility = VISIBLE
            contentWarningButton.visibility = VISIBLE
            setContentWarningButtonText(viewData.isExpanded)
            contentWarningButton.setOnClickListener {
                toggleExpandedState(
                    setStatusContent,
                    glide,
                    viewData,
                    true,
                    !expanded,
                    statusDisplayOptions,
                    listener,
                )
            }
            setTextVisible(setStatusContent, glide, true, viewData, statusDisplayOptions, listener)
            return
        }

        contentWarningDescription.visibility = GONE
        contentWarningButton.visibility = GONE
        setTextVisible(
            setStatusContent,
            glide,
            sensitive = false,
            viewData = viewData,
            statusDisplayOptions = statusDisplayOptions,
            listener = listener,
        )
    }

    private fun setContentWarningButtonText(expanded: Boolean) {
        if (expanded) {
            contentWarningButton.setText(R.string.post_content_warning_show_less)
        } else {
            contentWarningButton.setText(R.string.post_content_warning_show_more)
        }
    }

    fun toggleExpandedState(
        setStatusContent: SetStatusContent,
        glide: RequestManager,
        viewData: T,
        sensitive: Boolean,
        expanded: Boolean,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener<T>,
    ) {
        contentWarningDescription.invalidate()
        listener.onExpandedChange(viewData, expanded)
        setContentWarningButtonText(expanded)
        setTextVisible(setStatusContent, glide, sensitive, viewData, statusDisplayOptions, listener)
        setupCard(
            glide,
            viewData,
            expanded,
            statusDisplayOptions.cardViewMode,
            statusDisplayOptions,
            listener,
        )
    }

    private fun setTextVisible(
        setStatusContent: SetStatusContent,
        glide: RequestManager,
        sensitive: Boolean,
        viewData: T,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener<T>,
    ) {
        val emojis = viewData.actionable.emojis
        val mentions = viewData.actionable.mentions
        val tags = viewData.actionable.tags
        val poll = viewData.actionable.poll

        when (viewData.translationState) {
            TranslationState.SHOW_ORIGINAL -> translationProvider.hide()
            TranslationState.TRANSLATING -> {
                translationProvider.apply {
                    text = context.getString(R.string.translating)
                    show()
                }
            }

            TranslationState.SHOW_TRANSLATION -> {
                translationProvider.apply {
                    viewData.translation?.provider?.let {
                        text = context.getString(R.string.translation_provider_fmt, it.unicodeWrap())
                        show()
                    }
                }
            }
        }

        if (!sensitive || viewData.isExpanded) {
            setStatusContent(
                glide,
                this.content,
                viewData.content,
                statusDisplayOptions,
                emojis,
                mentions,
                tags,
                listener,
            )

            poll?.let {
                val pollViewData = if (viewData.translationState == TranslationState.SHOW_TRANSLATION) {
                    from(poll).copy(translatedPoll = viewData.translation?.poll)
                } else {
                    from(poll)
                }

                pollView.bind(
                    glide,
                    pollViewData,
                    emojis,
                    statusDisplayOptions,
                    NUMBER_FORMATTER,
                    TIME_FORMATTER,
                ) { choices ->
                    choices?.let {
                        listener.onVoteInPoll(viewData, poll, it)
                    } ?: listener.onViewThread(viewData.actionable)
                }
            } ?: pollView.hide()
        } else {
            pollView.hide()
            setClickableMentions(this.content, mentions, listener)
        }
        if (TextUtils.isEmpty(this.content.text)) {
            this.content.visibility = GONE
        } else {
            this.content.visibility = VISIBLE
        }
    }

    private fun setAvatar(
        glide: RequestManager,
        url: String,
        rebloggedUrl: String?,
        isBot: Boolean,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        val avatarRadius: Int
        if (rebloggedUrl.isNullOrBlank()) {
            avatar.setPaddingRelative(0, 0, 0, 0)
            if (statusDisplayOptions.showBotOverlay && isBot) {
                avatarInset.visibility = VISIBLE
                glide.load(DR.drawable.bot_badge)
                    .into(avatarInset)
            } else {
                avatarInset.visibility = GONE
            }
            avatarRadius = avatarRadius48dp
        } else {
            val padding = dpToPx(12f, context.resources.displayMetrics).toInt()
            avatar.setPaddingRelative(0, 0, padding, padding)
            avatarInset.visibility = VISIBLE
            avatarInset.background = null
            loadAvatar(
                glide,
                rebloggedUrl,
                avatarInset,
                avatarRadius24dp,
                statusDisplayOptions.animateAvatars,
            )
            avatarRadius = avatarRadius36dp
        }
        loadAvatar(
            glide,
            url,
            avatar,
            avatarRadius,
            statusDisplayOptions.animateAvatars,
            listOf(CompositeWithOpaqueBackground(MaterialColors.getColor(avatar, android.R.attr.colorBackground))),
        )
    }

    open fun setMetaData(
        viewData: T,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener<T>,
    ) {
        val createdAt = viewData.actionable.createdAt
        val editedAt = viewData.actionable.editedAt

        var timestampText: String
        timestampText = if (statusDisplayOptions.useAbsoluteTime) {
            TIME_FORMATTER.format(createdAt, true)
        } else {
            val then = createdAt.time
            val now = System.currentTimeMillis()
            getRelativeTimeSpanString(context, then, now)
        }
        editedAt?.also {
            timestampText = context.getString(
                R.string.post_timestamp_with_edited_indicator,
                timestampText,
            )
        }
        metaInfo.text = timestampText
    }

    /**
     * Shows/hides the chips for any roles the account that posted the actionable
     * status has.
     *
     * @param viewData
     */
    private fun setRoleChips(viewData: T) {
        roleChipGroup.setRoles(viewData.actionable.account.roles, viewData.actionable.account.domain)
    }

    private fun getCreatedAtDescription(
        createdAt: Date?,
        statusDisplayOptions: StatusDisplayOptions,
    ): CharSequence {
        return if (statusDisplayOptions.useAbsoluteTime) {
            TIME_FORMATTER.format(createdAt, true)
        } else {
            /* This one is for screen-readers. Frequently, they would mispronounce timestamps like "17m"
             * as 17 meters instead of minutes. */
            createdAt?.let {
                val then = createdAt.time
                val now = System.currentTimeMillis()
                DateUtils.getRelativeTimeSpanString(
                    then,
                    now,
                    DateUtils.SECOND_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                )
            } ?: "? minutes"
        }
    }

    /**
     * Shows/hides the media previews based on the attachments, and wires
     * up click listeners.
     *
     * If the user has disabled media previews an icon is shown for
     * each attachment, along with the attachment's description.
     *
     * Otherwise the attachment is shown or hidden depending on
     * [viewdData.attachmentDisplayAction][StatusViewData.attachmentDisplayAction].
     *
     * @param viewData
     * @param mediaPreviewEnabled True if the user has enabled attachment previews
     * @param listener
     * @param useBlurhash If true and
     * [viewdData.attachmentDisplayAction][StatusViewData.attachmentDisplayAction]
     * is [Hide][AttachmentDisplayAction.Hide] the attachment's blur hash will be
     * shown. Otherwise a solid colour is used.
     */
    fun setMediaPreviews(
        glide: RequestManager,
        viewData: T,
        mediaPreviewEnabled: Boolean,
        listener: StatusActionListener<T>,
        useBlurhash: Boolean,
    ) {
        // Get the attachments -- this might be the translated version (with any
        // translated descriptions).
        val actionable = viewData.actionable
        val attachments = if (viewData.translationState == TranslationState.SHOW_TRANSLATION) {
            viewData.translation?.attachments?.zip(actionable.attachments) { t, a ->
                a.copy(description = t.description)
            } ?: actionable.attachments
        } else {
            actionable.attachments
        }

        attachmentsView.bind(
            glide,
            viewData,
            attachments,
            mediaPreviewEnabled,
            useBlurhash,
            onViewAttachment = listener::onViewAttachment,
            onAttachmentDisplayActionChange = listener::onAttachmentDisplayActionChange,
        )
    }

    fun setupButtons(
        viewData: T,
        listener: StatusActionListener<T>,
        accountId: String,
    ) {
        val profileButtonClickListener = View.OnClickListener { listener.onViewAccount(accountId) }
        avatar.setOnClickListener(profileButtonClickListener)
        displayName.setOnClickListener(profileButtonClickListener)

        /* Even though the content TextView is a child of the container, it won't respond to clicks
         * if it contains URLSpans without also setting its listener. The surrounding spans will
         * just eat the clicks instead of deferring to the parent listener, but WILL respond to a
         * listener directly on the TextView, for whatever reason. */
        val viewThreadListener = View.OnClickListener {
            listener.onViewThread(viewData.actionable)
        }

        content.setOnClickListener(viewThreadListener)
    }

    private fun setupCollapsedState(
        viewData: T,
        sensitive: Boolean,
        listener: StatusActionListener<T>,
    ) {
        val buttonToggleContent = buttonToggleContent ?: return

        /* input filter for TextViews have to be set before text */
        if (viewData.isCollapsible && (!sensitive || viewData.isExpanded)) {
            buttonToggleContent.setOnClickListener {
                listener.onContentCollapsedChange(viewData, !viewData.isCollapsed)
            }
            buttonToggleContent.show()
            if (viewData.isCollapsed) {
                buttonToggleContent.setText(R.string.post_content_warning_show_more)
                content.filters = COLLAPSE_INPUT_FILTER
            } else {
                buttonToggleContent.setText(R.string.post_content_warning_show_less)
                content.filters = NO_INPUT_FILTER
            }
        } else {
            buttonToggleContent.hide()
            content.filters = NO_INPUT_FILTER
        }
    }

    open fun setupWithStatus(
        setStatusContent: SetStatusContent,
        glide: RequestManager,
        viewData: T,
        listener: StatusActionListener<T>,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        val actionable = viewData.actionable
        setDisplayName(glide, actionable.account.name, actionable.account.emojis, statusDisplayOptions)
        setUsername(actionable.account.username)
        setMetaData(viewData, statusDisplayOptions, listener)
        setRoleChips(viewData)
        setAvatar(
            glide,
            actionable.account.avatar,
            viewData.rebloggedAvatar,
            actionable.account.bot,
            statusDisplayOptions,
        )
        setMediaPreviews(
            glide,
            viewData,
            statusDisplayOptions.mediaPreviewEnabled,
            listener,
            statusDisplayOptions.useBlurhash,
        )
        setupCard(
            glide,
            viewData,
            viewData.isExpanded,
            statusDisplayOptions.cardViewMode,
            statusDisplayOptions,
            listener,
        )
        setupButtons(
            viewData,
            listener,
            actionable.account.id,
        )
        val sensitive = !TextUtils.isEmpty(viewData.actionable.spoilerText)
        setupCollapsedState(viewData, sensitive, listener)

        setSpoilerAndContent(setStatusContent, glide, viewData, statusDisplayOptions, listener)
    }

    /** Creates the content description for the status. */
    fun getContentDescription(viewData: T, statusDisplayOptions: StatusDisplayOptions): CharSequence {
        val account = viewData.actionable.account
        val createdAt = viewData.actionable.createdAt
        val editedAt = viewData.actionable.editedAt
        val reblogsCount = viewData.actionable.reblogsCount
        val favouritesCount = viewData.actionable.favouritesCount
        val reblogged = viewData.actionable.reblogged
        val favourited = viewData.actionable.favourited
        val bookmarked = viewData.actionable.bookmarked
        val sensitive = viewData.actionable.sensitive
        val visibility = viewData.actionable.visibility
        val spoilerText = viewData.actionable.spoilerText

        // Build the content description using a string builder instead of a string resource
        // as there are many places where optional ";" and "," are needed.
        //
        // The full string will look like (content in parentheses is optional),
        //
        // account.name
        // (; "Roles: " account.roles)
        // (. contentWarning)
        // ; (content)
        // (, poll)
        // , relativeDate
        // (, editedAt)
        // (, reblogDescription)
        // , username
        // (, "Reblogged")
        // (, "Favourited")
        // (, "Bookmarked")
        // (, "n Favorite")
        // (, "n Boost")
        return StringBuilder().apply {
            append(account.contentDescription(context))

            append(".")

            getContentWarningDescription(context, viewData)?.let { append("; ", it) }

            append("; ")

            // Content is optional, and hidden if there are spoilers or the status is
            // marked sensitive, and it has not been expanded.
            if (TextUtils.isEmpty(spoilerText) || !sensitive || viewData.isExpanded) {
                append(viewData.content.parseAsMastodonHtml(), ", ")
            }

            viewData.actionable.poll?.let {
                append(
                    pollView.getPollDescription(
                        from(it),
                        statusDisplayOptions,
                        NUMBER_FORMATTER,
                        TIME_FORMATTER,
                    ),
                    ", ",
                )
            }

            append(getCreatedAtDescription(createdAt, statusDisplayOptions))

            editedAt?.let { append(", ", context.getString(R.string.description_post_edited)) }

            getReblogDescription(context, viewData)?.let { append(", ", it) }

            if (reblogged) {
                append(", ", context.getString(R.string.description_post_reblogged))
            }

            if (favourited) {
                append(", ", context.getString(R.string.description_post_favourited))
            }
            if (bookmarked) {
                append(", ", context.getString(R.string.description_post_bookmarked))
            }
            getMediaDescription(context, viewData)?.let { append(", ", it) }

            append("; ")

            visibility.description(context)?.let { append(it) }
            getFavouritesCountDescription(favouritesCount)?.let { append(", ", it) }
            getReblogsCountDescription(reblogsCount)?.let { append(", ", it) }
        }
    }

    /** @return "n Favourite", for use in a content description. */
    fun getFavouritesCountDescription(count: Int): CharSequence? {
        if (count <= 0) return null

        val countString = NUMBER_FORMATTER.format(count.toLong())
        return HtmlCompat.fromHtml(
            context.resources.getQuantityString(R.plurals.favs, count, countString),
            HtmlCompat.FROM_HTML_MODE_LEGACY,
        )
    }

    /** @return "n Boost", for use in a content description. */
    fun getReblogsCountDescription(count: Int): CharSequence? {
        if (count <= 0) return null

        val countString = NUMBER_FORMATTER.format(count.toLong())
        return HtmlCompat.fromHtml(
            context.resources.getQuantityString(
                R.plurals.reblogs,
                count,
                countString,
            ),
            HtmlCompat.FROM_HTML_MODE_LEGACY,
        )
    }

    open fun setupCard(
        glide: RequestManager,
        viewData: T,
        expanded: Boolean,
        cardViewMode: CardViewMode,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener<T>,
    ) {
        val sensitive = viewData.actionable.sensitive
        val attachments = viewData.actionable.attachments
        val poll = viewData.actionable.poll
        val card = viewData.actionable.card

        if (cardViewMode !== CardViewMode.NONE &&
            attachments.isEmpty() &&
            poll == null &&
            card != null &&
            !TextUtils.isEmpty(card.url) &&
            (!sensitive || expanded) &&
            (!viewData.isCollapsible || !viewData.isCollapsed)
        ) {
            cardView.visibility = VISIBLE
            cardView.bind(glide, card, viewData.actionable.sensitive, statusDisplayOptions, false) { card, target ->
                if (target == PreviewCardView.Target.BYLINE) {
                    card.authors?.firstOrNull()?.account?.id?.let { listener.onViewAccount(it) }
                    return@bind
                }

                if (card.kind == PreviewCardKind.PHOTO && card.embedUrl.isNotEmpty() && target == PreviewCardView.Target.IMAGE) {
                    listener.onViewMedia(viewData.pachliAccountId, viewData.actionable.account.username, card.embedUrl)
                    return@bind
                }

                listener.onViewUrl(card.url)
            }
        } else {
            cardView.visibility = GONE
        }
    }

    companion object {
        /** @return "{account.username} boosted", for use in a content description. */
        private fun getReblogDescription(context: Context, status: IStatusViewData): String? {
            return status.rebloggingStatus?.let {
                context.getString(R.string.post_boosted_format, it.account.username)
            }
        }

        /** @return "Media: {0-n descriptions}", for use in a content description. */
        private fun getMediaDescription(context: Context, status: IStatusViewData): String? {
            if (status.actionable.attachments.isEmpty()) return null

            val attachmentDisplayAction = status.attachmentDisplayAction
            return when (attachmentDisplayAction) {
                is AttachmentDisplayAction.Show -> {
                    val descriptions = status.actionable.attachments.map { it.getContentDescription(context) }
                    context.getString(R.string.description_post_media, descriptions.joinToString(", "))
                }

                is AttachmentDisplayAction.Hide -> context.getString(
                    R.string.description_post_media,
                    attachmentDisplayAction.reason.getFormattedDescription(context),
                )
            }
        }

        /** @return "Content warning: {spoilerText}", for use in a content description. */
        private fun getContentWarningDescription(context: Context, status: IStatusViewData): String? {
            val spoilerText = status.actionable.spoilerText
            if (spoilerText.isEmpty()) return null

            return context.getString(R.string.description_post_cw, status.spoilerText)
        }
    }
}

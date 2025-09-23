package app.pachli.adapter

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.drawable.toDrawable
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.visible
import app.pachli.core.common.string.unicodeWrap
import app.pachli.core.common.util.AbsoluteTimeFormatter
import app.pachli.core.common.util.formatNumber
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.database.model.TranslationState
import app.pachli.core.designsystem.R as DR
import app.pachli.core.model.Attachment
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.AttachmentDisplayReason
import app.pachli.core.model.Emoji
import app.pachli.core.model.PreviewCardKind
import app.pachli.core.model.Status
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.preferences.CardViewMode
import app.pachli.core.ui.CompositeWithOpaqueBackground
import app.pachli.core.ui.MediaPreviewImageView
import app.pachli.core.ui.MediaPreviewLayout
import app.pachli.core.ui.PollView
import app.pachli.core.ui.PollViewData.Companion.from
import app.pachli.core.ui.PreviewCardView
import app.pachli.core.ui.SetStatusContent
import app.pachli.core.ui.StatusActionListener
import app.pachli.core.ui.decodeBlurHash
import app.pachli.core.ui.emojify
import app.pachli.core.ui.extensions.aspectRatios
import app.pachli.core.ui.extensions.getFormattedDescription
import app.pachli.core.ui.extensions.iconResource
import app.pachli.core.ui.extensions.isPlayable
import app.pachli.core.ui.extensions.setRoles
import app.pachli.core.ui.getRelativeTimeSpanString
import app.pachli.core.ui.loadAvatar
import app.pachli.core.ui.makeIcon
import app.pachli.core.ui.setClickableMentions
import app.pachli.util.description
import app.pachli.util.expandTouchSizeToFillRow
import at.connyduck.sparkbutton.SparkButton
import at.connyduck.sparkbutton.helpers.Utils
import com.bumptech.glide.RequestManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.mikepenz.iconics.typeface.library.googlematerial.GoogleMaterial
import java.text.NumberFormat
import java.util.Date

abstract class StatusBaseViewHolder<T : IStatusViewData> protected constructor(
    itemView: View,
    protected val glide: RequestManager,
    protected val setStatusContent: SetStatusContent,
) : RecyclerView.ViewHolder(itemView) {
    protected val context: Context = itemView.context
    private val displayName: TextView = itemView.findViewById(R.id.status_display_name)
    private val username: TextView = itemView.findViewById(R.id.status_username)
    private val roleChipGroup: ChipGroup = itemView.findViewById(R.id.roleChipGroup)
    private val replyButton: ImageButton = itemView.findViewById(R.id.status_reply)
    private val replyCountLabel: TextView? = itemView.findViewById(R.id.status_replies)
    private val reblogButton: SparkButton? = itemView.findViewById(R.id.status_inset)
    private val favouriteButton: SparkButton = itemView.findViewById(R.id.status_favourite)
    private val bookmarkButton: SparkButton = itemView.findViewById(R.id.status_bookmark)
    private val moreButton: ImageButton = itemView.findViewById(R.id.status_more)

    /** [MediaPreviewLayout] that encompasses and lays out all the attachment previews. */
    private val mediaPreview: MediaPreviewLayout = itemView.findViewById(app.pachli.core.ui.R.id.status_media_preview)

    /**
     * [TextView] that overlays attachment previews when hidden/blurred to explain why
     * they are hidden/blurred.
     */
    private val sensitiveMediaWarning: TextView = itemView.findViewById(R.id.status_sensitive_media_warning)

    /**
     * [ImageView] showing an icon the user can click on to toggle between showing or
     * hiding the attachment preview.
     */
    private val sensitiveMediaShow: ImageView = itemView.findViewById(R.id.status_sensitive_media_button)

    /** Views for displaying the description of the attachment at that index. */
    private val mediaDescriptionViews: Array<TextView> = arrayOf(
        itemView.findViewById(app.pachli.core.ui.R.id.status_media_label_0),
        itemView.findViewById(app.pachli.core.ui.R.id.status_media_label_1),
        itemView.findViewById(app.pachli.core.ui.R.id.status_media_label_2),
        itemView.findViewById(app.pachli.core.ui.R.id.status_media_label_3),
    )

    private val contentWarningButton: MaterialButton = itemView.findViewById(R.id.status_content_warning_button)
    private val avatarInset: ImageView = itemView.findViewById(R.id.status_avatar_inset)
    val avatar: ImageView = itemView.findViewById(R.id.status_avatar)
    val metaInfo: TextView = itemView.findViewById(R.id.status_meta_info)
    val content: TextView = itemView.findViewById(R.id.status_content)
    private val contentWarningDescription: TextView = itemView.findViewById(R.id.status_content_warning_description)
    private val pollView: PollView = itemView.findViewById(R.id.status_poll)
    private val cardView: PreviewCardView? = itemView.findViewById(R.id.status_card_view)
    private val numberFormat = NumberFormat.getNumberInstance()
    private val absoluteTimeFormatter = AbsoluteTimeFormatter()
    private val translationProvider: TextView? = itemView.findViewById<TextView?>(R.id.translationProvider)?.apply {
        val icon = makeIcon(context, GoogleMaterial.Icon.gmd_translate, textSize.toInt())
        setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
    }
    protected val avatarRadius48dp = context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_48dp)
    private val avatarRadius36dp = context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_36dp)
    private val avatarRadius24dp = context.resources.getDimensionPixelSize(DR.dimen.avatar_radius_24dp)
    private val mediaPreviewUnloaded = MaterialColors.getColor(itemView, android.R.attr.textColorLink).toDrawable()

    init {
        (itemView as ViewGroup).expandTouchSizeToFillRow(
            listOfNotNull(
                replyButton,
                reblogButton,
                favouriteButton,
                bookmarkButton,
                moreButton,
            ),
        )
    }

    protected fun setDisplayName(
        name: String,
        customEmojis: List<Emoji>?,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        displayName.text = name.emojify(glide, customEmojis, displayName, statusDisplayOptions.animateEmojis)
    }

    protected fun setUsername(name: String) {
        username.text = context.getString(DR.string.post_username_format, name)
    }

    fun toggleContentWarning() {
        contentWarningButton.performClick()
    }

    protected fun setSpoilerAndContent(
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
            contentWarningDescription.visibility = View.VISIBLE
            contentWarningButton.visibility = View.VISIBLE
            setContentWarningButtonText(viewData.isExpanded)
            contentWarningButton.setOnClickListener {
                toggleExpandedState(
                    viewData,
                    true,
                    !expanded,
                    statusDisplayOptions,
                    listener,
                )
            }
            setTextVisible(true, viewData, statusDisplayOptions, listener)
            return
        }

        contentWarningDescription.visibility = View.GONE
        contentWarningButton.visibility = View.GONE
        setTextVisible(
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

    protected open fun toggleExpandedState(
        viewData: T,
        sensitive: Boolean,
        expanded: Boolean,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener<T>,
    ) {
        contentWarningDescription.invalidate()
        listener.onExpandedChange(viewData, expanded)
        setContentWarningButtonText(expanded)
        setTextVisible(sensitive, viewData, statusDisplayOptions, listener)
        setupCard(
            viewData,
            expanded,
            statusDisplayOptions.cardViewMode,
            statusDisplayOptions,
            listener,
        )
    }

    private fun setTextVisible(
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
            TranslationState.SHOW_ORIGINAL -> translationProvider?.hide()
            TranslationState.TRANSLATING -> {
                translationProvider?.apply {
                    text = context.getString(R.string.translating)
                    show()
                }
            }
            TranslationState.SHOW_TRANSLATION -> {
                translationProvider?.apply {
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
                    numberFormat,
                    absoluteTimeFormatter,
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
            this.content.visibility = View.GONE
        } else {
            this.content.visibility = View.VISIBLE
        }
    }

    private fun setAvatar(
        url: String,
        rebloggedUrl: String?,
        isBot: Boolean,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        val avatarRadius: Int
        if (TextUtils.isEmpty(rebloggedUrl)) {
            avatar.setPaddingRelative(0, 0, 0, 0)
            if (statusDisplayOptions.showBotOverlay && isBot) {
                avatarInset.visibility = View.VISIBLE
                glide.load(DR.drawable.bot_badge)
                    .into(avatarInset)
            } else {
                avatarInset.visibility = View.GONE
            }
            avatarRadius = avatarRadius48dp
        } else {
            val padding = Utils.dpToPx(context, 12)
            avatar.setPaddingRelative(0, 0, padding, padding)
            avatarInset.visibility = View.VISIBLE
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

    protected open fun setMetaData(
        viewData: T,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener<T>,
    ) {
        val createdAt = viewData.actionable.createdAt
        val editedAt = viewData.actionable.editedAt

        var timestampText: String
        timestampText = if (statusDisplayOptions.useAbsoluteTime) {
            absoluteTimeFormatter.format(createdAt, true)
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
        roleChipGroup.setRoles(viewData.actionable.account.roles)
    }

    private fun getCreatedAtDescription(
        createdAt: Date?,
        statusDisplayOptions: StatusDisplayOptions,
    ): CharSequence {
        return if (statusDisplayOptions.useAbsoluteTime) {
            absoluteTimeFormatter.format(createdAt, true)
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

    protected fun setIsReply(isReply: Boolean) {
        val drawable = if (isReply) R.drawable.ic_reply_all_24dp else R.drawable.ic_reply_24dp
        replyButton.setImageResource(drawable)
    }

    private fun setReplyCount(repliesCount: Int, fullStats: Boolean) {
        // This label only exists in the non-detailed view (to match the web ui)
        replyCountLabel ?: return

        if (fullStats) {
            replyCountLabel.text = formatNumber(repliesCount.toLong(), 1000)
            return
        }

        // Show "0", "1", or "1+" for replies otherwise, so the user knows if there is a thread
        // that they can click through to read.
        replyCountLabel.text =
            if (repliesCount > 1) context.getString(R.string.status_count_one_plus) else repliesCount.toString()
    }

    private fun setReblogged(reblogged: Boolean) {
        reblogButton!!.isChecked = reblogged
    }

    // This should only be called after setReblogged, in order to override the tint correctly.
    private fun setRebloggingEnabled(enabled: Boolean, visibility: Status.Visibility) {
        reblogButton!!.isEnabled = enabled && visibility !== Status.Visibility.PRIVATE
        if (enabled) {
            val inactiveId: Int
            val activeId: Int
            if (visibility === Status.Visibility.PRIVATE) {
                inactiveId = R.drawable.ic_reblog_private_24dp
                activeId = R.drawable.ic_reblog_private_active_24dp
            } else {
                inactiveId = R.drawable.ic_reblog_24dp
                activeId = R.drawable.ic_reblog_active_24dp
            }
            reblogButton.setInactiveImage(inactiveId)
            reblogButton.setActiveImage(activeId)
            return
        }

        val disabledId: Int = if (visibility === Status.Visibility.DIRECT) {
            R.drawable.ic_reblog_direct_24dp
        } else {
            R.drawable.ic_reblog_private_24dp
        }
        reblogButton.setInactiveImage(disabledId)
        reblogButton.setActiveImage(disabledId)
    }

    protected fun setFavourited(favourited: Boolean) {
        favouriteButton.isChecked = favourited
    }

    protected fun setBookmarked(bookmarked: Boolean) {
        bookmarkButton.isChecked = bookmarked
    }

    private fun decodeBlurHash(blurhash: String): BitmapDrawable? {
        return decodeBlurHash(context, blurhash)
    }

    private fun loadImage(
        imageView: MediaPreviewImageView,
        previewUrl: String?,
        focus: Attachment.Focus?,
        blurhash: String?,
    ) {
        val placeholder = blurhash?.let { decodeBlurHash(it) } ?: mediaPreviewUnloaded
        if (TextUtils.isEmpty(previewUrl)) {
            imageView.removeFocalPoint()
            glide.load(placeholder)
                .centerInside()
                .into(imageView)
            return
        }

        if (focus != null) { // If there is a focal point for this attachment:
            imageView.setFocalPoint(focus)
            glide.load(previewUrl)
                .placeholder(placeholder)
                .centerInside()
                .addListener(imageView)
                .into(imageView)
        } else {
            imageView.removeFocalPoint()
            glide.load(previewUrl)
                .placeholder(placeholder)
                .centerInside()
                .into(imageView)
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
    protected fun setMediaPreviews(
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

        val previewableAttachments = attachments.filter { it.isPreviewable() }.take(4)

        // Disable all previews if the user has disabled media previews or there
        // are no previewable attachments. Display attachment descriptions instead.
        if (!mediaPreviewEnabled || previewableAttachments.isEmpty()) {
            setMediaLabel(viewData, attachments, listener)
            mediaPreview.hide()
            hideSensitiveMediaWarning()
            return
        }

        // Previewable attachments exist. Hide all the description fields.
        mediaDescriptionViews.forEach { it.hide() }

        mediaPreview.show()
        mediaPreview.aspectRatios = previewableAttachments.aspectRatios()
        mediaPreview.forEachIndexed { i: Int, imageView: MediaPreviewImageView, descriptionIndicator: TextView ->
            // Loads each attachment in to the correct imageView.
            val attachment = attachments[i]
            val previewUrl = attachment.previewUrl
            val description = attachment.description
            val hasDescription = description?.isNotBlank() == true
            if (hasDescription) {
                imageView.contentDescription = description
            } else {
                imageView.contentDescription = context.getString(R.string.action_view_media)
            }

            val displayAction = viewData.attachmentDisplayAction
            val showContent = displayAction is AttachmentDisplayAction.Show

            loadImage(
                imageView,
                if (showContent) previewUrl else null,
                attachment.meta?.focus,
                if (useBlurhash) attachment.blurhash else null,
            )
            val type = attachment.type
            if (showContent && type.isPlayable()) {
                imageView.foreground = AppCompatResources.getDrawable(context, R.drawable.play_indicator_overlay)
            } else {
                imageView.foreground = null
            }
            setAttachmentClickListener(viewData, imageView, listener, i, attachment, true)

            when (displayAction) {
                is AttachmentDisplayAction.Show -> sensitiveMediaWarning.hide()

                is AttachmentDisplayAction.Hide -> {
                    val text = when (val reason = displayAction.reason) {
                        is AttachmentDisplayReason.BlurFilter -> {
                            val resource = when (reason.filters.size) {
                                1 -> R.string.attachment_matches_filter_one_fmt
                                2 -> R.string.attachment_matches_filter_two_fmt
                                else -> R.string.attachment_matches_filter_other_fmt
                            }
                            context.getString(resource, *reason.filters.map { it.title }.toTypedArray())
                        }
                        is AttachmentDisplayReason.Sensitive -> context.getText(R.string.post_sensitive_media_title)
                        is AttachmentDisplayReason.UserAction -> context.getText(R.string.attachment_hidden_user_action)
                    }
                    sensitiveMediaWarning.text = text
                    sensitiveMediaWarning.show()
                }
            }

            descriptionIndicator.visible(hasDescription && showContent)
            sensitiveMediaShow.visible(showContent)
            sensitiveMediaShow.setOnClickListener { v: View ->
                // The user clicked to hide the attachment. Either they are:
                //
                // a. Re-hiding an attachment that was hidden that they decided to show, or
                // b. Hiding media that wasn't originally hidden.
                //
                // If (a) then the new decision is `Show.originalDecision`. If (b) then
                // then the new decision is UserAction.
                val decision = (viewData.attachmentDisplayAction as? AttachmentDisplayAction.Show)?.originalAction
                    ?: AttachmentDisplayAction.Hide(AttachmentDisplayReason.UserAction)
                listener.onAttachmentDisplayActionChange(viewData, decision)
            }
            sensitiveMediaWarning.setOnClickListener { v: View ->
                // The user is clicking through the warning to show the attachment.
                listener.onAttachmentDisplayActionChange(
                    viewData,
                    AttachmentDisplayAction.Show(originalAction = viewData.attachmentDisplayAction as? AttachmentDisplayAction.Hide),
                )
            }
        }
    }

    /**
     * Sets the label (icon and text) to display for the attachments, assuming
     * that none of them are previewable, or the user has chosen not to show
     * previews.
     */
    private fun setMediaLabel(
        viewData: T,
        attachments: List<Attachment>,
        listener: StatusActionListener<T>,
    ) {
        val sensitive = viewData.actionable.sensitive
        mediaDescriptionViews.forEachIndexed { index, mediaLabel ->
            if (index < attachments.size) {
                val attachment = attachments[index]
                mediaLabel.show()

                val mediaDescription = if (sensitive) {
                    context.getString(R.string.post_sensitive_media_title)
                } else {
                    attachment.getFormattedDescription(context)
                }
                mediaDescriptionViews[index].text = mediaDescription

                // Set the icon next to the label.
                val drawableId = attachment.iconResource()
                mediaLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(drawableId, 0, 0, 0)
                setAttachmentClickListener(viewData, mediaLabel, listener, index, attachment, false)
            } else {
                mediaLabel.hide()
            }
        }
    }

    private fun setAttachmentClickListener(
        viewData: T,
        view: View,
        listener: StatusActionListener<T>,
        index: Int,
        attachment: Attachment,
        animateTransition: Boolean,
    ) {
        view.setOnClickListener { v: View? ->
            if (sensitiveMediaWarning.isVisible) {
                listener.onAttachmentDisplayActionChange(
                    viewData,
                    AttachmentDisplayAction.Show(originalAction = viewData.attachmentDisplayAction as? AttachmentDisplayAction.Hide),
                )
            } else {
                listener.onViewMedia(viewData, index, if (animateTransition) v else null)
            }
        }
        view.setOnLongClickListener {
            val description = attachment.getFormattedDescription(view.context)
            Toast.makeText(view.context, description, Toast.LENGTH_LONG).show()
            true
        }
    }

    /** Hides [sensitiveMediaWarning] and [sensitiveMediaShow]. */
    protected fun hideSensitiveMediaWarning() {
        sensitiveMediaWarning.hide()
        sensitiveMediaShow.hide()
    }

    protected fun setupButtons(
        viewData: T,
        listener: StatusActionListener<T>,
        accountId: String,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        val profileButtonClickListener = View.OnClickListener { listener.onViewAccount(accountId) }
        avatar.setOnClickListener(profileButtonClickListener)
        displayName.setOnClickListener(profileButtonClickListener)
        replyButton.setOnClickListener {
            listener.onReply(viewData)
        }
        reblogButton?.setEventListener { _: SparkButton?, buttonState: Boolean ->
            // return true to play animation
            return@setEventListener if (statusDisplayOptions.confirmReblogs) {
                showConfirmReblog(viewData, listener, buttonState)
                false
            } else {
                listener.onReblog(viewData, !buttonState)
                true
            }
        }
        favouriteButton.setEventListener { _: SparkButton?, buttonState: Boolean ->
            // return true to play animation
            return@setEventListener if (statusDisplayOptions.confirmFavourites) {
                showConfirmFavourite(viewData, listener, buttonState)
                false
            } else {
                listener.onFavourite(viewData, !buttonState)
                true
            }
        }
        bookmarkButton.setEventListener { _: SparkButton?, buttonState: Boolean ->
            listener.onBookmark(viewData, !buttonState)
            true
        }
        moreButton.setOnClickListener { v: View? ->
            listener.onMore(v!!, viewData)
        }

        /* Even though the content TextView is a child of the container, it won't respond to clicks
         * if it contains URLSpans without also setting its listener. The surrounding spans will
         * just eat the clicks instead of deferring to the parent listener, but WILL respond to a
         * listener directly on the TextView, for whatever reason. */
        val viewThreadListener = View.OnClickListener {
            listener.onViewThread(viewData.actionable)
        }

        content.setOnClickListener(viewThreadListener)
        itemView.setOnClickListener(viewThreadListener)
    }

    private fun showConfirmReblog(
        viewData: T,
        listener: StatusActionListener<T>,
        buttonState: Boolean,
    ) {
        val popup = PopupMenu(context, reblogButton!!)
        popup.inflate(R.menu.status_reblog)
        val menu = popup.menu
        if (buttonState) {
            menu.findItem(R.id.menu_action_reblog).isVisible = false
        } else {
            menu.findItem(R.id.menu_action_unreblog).isVisible = false
        }
        popup.setOnMenuItemClickListener {
            listener.onReblog(viewData, !buttonState)
            if (!buttonState) {
                reblogButton.playAnimation()
            }
            true
        }
        popup.show()
    }

    private fun showConfirmFavourite(
        viewData: T,
        listener: StatusActionListener<T>,
        buttonState: Boolean,
    ) {
        val popup = PopupMenu(context, favouriteButton)
        popup.inflate(R.menu.status_favourite)
        val menu = popup.menu
        if (buttonState) {
            menu.findItem(R.id.menu_action_favourite).isVisible = false
        } else {
            menu.findItem(R.id.menu_action_unfavourite).isVisible = false
        }
        popup.setOnMenuItemClickListener {
            listener.onFavourite(viewData, !buttonState)
            if (!buttonState) {
                favouriteButton.playAnimation()
            }
            true
        }
        popup.show()
    }

    open fun setupWithStatus(
        viewData: T,
        listener: StatusActionListener<T>,
        statusDisplayOptions: StatusDisplayOptions,
        payloads: List<List<Any?>>?,
    ) {
        if (payloads.isNullOrEmpty()) {
            val actionable = viewData.actionable
            setDisplayName(actionable.account.name, actionable.account.emojis, statusDisplayOptions)
            setUsername(actionable.account.username)
            setMetaData(viewData, statusDisplayOptions, listener)
            setRoleChips(viewData)
            setIsReply(actionable.inReplyToId != null)
            setReplyCount(actionable.repliesCount, statusDisplayOptions.showStatsInline)
            setAvatar(
                actionable.account.avatar,
                viewData.rebloggedAvatar,
                actionable.account.bot,
                statusDisplayOptions,
            )
            setReblogged(actionable.reblogged)
            setFavourited(actionable.favourited)
            setBookmarked(actionable.bookmarked)
            setMediaPreviews(
                viewData,
                statusDisplayOptions.mediaPreviewEnabled,

                listener,
                statusDisplayOptions.useBlurhash,
            )
            setupCard(
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
                statusDisplayOptions,
            )
            setRebloggingEnabled(actionable.rebloggingAllowed(), actionable.visibility)
            setSpoilerAndContent(viewData, statusDisplayOptions, listener)
            setContentDescriptionForStatus(viewData, statusDisplayOptions)

            // Workaround for RecyclerView 1.0.0 / androidx.core 1.0.0
            // RecyclerView tries to set AccessibilityDelegateCompat to null
            // but ViewCompat code replaces is with the default one. RecyclerView never
            // fetches another one from its delegate because it checks that it's set so we remove it
            // and let RecyclerView ask for a new delegate.
            itemView.accessibilityDelegate = null
        } else {
            payloads.flatten()?.forEach { item ->
                if (item == StatusViewDataDiffCallback.Payload.CREATED) {
                    setMetaData(viewData, statusDisplayOptions, listener)
                }
                if (item == StatusViewDataDiffCallback.Payload.ATTACHMENTS) {
                    setMediaPreviews(
                        viewData,
                        statusDisplayOptions.mediaPreviewEnabled,
                        listener,
                        statusDisplayOptions.useBlurhash,
                    )
                }
            }
        }
    }

    /** Creates and sets the content description for the status. */
    private fun setContentDescriptionForStatus(viewData: T, statusDisplayOptions: StatusDisplayOptions) {
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
        val description = StringBuilder().apply {
            append(account.name)

            if (account.roles.isNotEmpty()) {
                append("; ")
                append(
                    context.resources.getQuantityString(
                        R.plurals.description_post_roles,
                        account.roles.size,
                    ),
                )
                append(account.roles.joinToString(", ") { it.name })
            }

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
                        numberFormat,
                        absoluteTimeFormatter,
                    ),
                    ", ",
                )
            }

            append(getCreatedAtDescription(createdAt, statusDisplayOptions))

            editedAt?.let { append(", ", context.getString(R.string.description_post_edited)) }

            getReblogDescription(context, viewData)?.let { append(", ", it) }

            append(", ", viewData.actionable.account.username)

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

        itemView.contentDescription = description
    }

    /** @return "n Favourite", for use in a content description. */
    protected fun getFavouritesCountDescription(count: Int): CharSequence? {
        if (count <= 0) return null

        val countString = numberFormat.format(count.toLong())
        return HtmlCompat.fromHtml(
            context.resources.getQuantityString(R.plurals.favs, count, countString),
            HtmlCompat.FROM_HTML_MODE_LEGACY,
        )
    }

    /** @return "n Boost", for use in a content description. */
    protected fun getReblogsCountDescription(count: Int): CharSequence? {
        if (count <= 0) return null

        val countString = numberFormat.format(count.toLong())
        return HtmlCompat.fromHtml(
            context.resources.getQuantityString(
                R.plurals.reblogs,
                count,
                countString,
            ),
            HtmlCompat.FROM_HTML_MODE_LEGACY,
        )
    }

    protected fun setupCard(
        viewData: T,
        expanded: Boolean,
        cardViewMode: CardViewMode,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener<T>,
    ) {
        cardView ?: return

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
            cardView.visibility = View.VISIBLE
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
            cardView.visibility = View.GONE
        }
    }

    open fun showStatusContent(show: Boolean) {
        itemView.visibility = if (show) View.VISIBLE else View.GONE
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

            val missingDescription = context.getString(app.pachli.core.ui.R.string.description_post_media_no_description_placeholder)

            val mediaDescriptions = status.actionable.attachments.map {
                it.description ?: missingDescription
            }

            return context.getString(R.string.description_post_media, mediaDescriptions.joinToString(", "))
        }

        /** @return "Content warning: {spoilerText}", for use in a content description. */
        private fun getContentWarningDescription(context: Context, status: IStatusViewData): String? {
            val spoilerText = status.actionable.spoilerText
            if (spoilerText.isEmpty()) return null

            return context.getString(R.string.description_post_cw, status.spoilerText)
        }
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

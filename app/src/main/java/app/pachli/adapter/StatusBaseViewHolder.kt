package app.pachli.adapter

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.ViewMediaActivity.Companion.newSingleImageIntent
import app.pachli.entity.Attachment
import app.pachli.entity.Emoji
import app.pachli.entity.PreviewCardKind
import app.pachli.entity.Status
import app.pachli.entity.description
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.AbsoluteTimeFormatter
import app.pachli.util.CardViewMode
import app.pachli.util.CompositeWithOpaqueBackground
import app.pachli.util.StatusDisplayOptions
import app.pachli.util.aspectRatios
import app.pachli.util.decodeBlurHash
import app.pachli.util.emojify
import app.pachli.util.expandTouchSizeToFillRow
import app.pachli.util.formatNumber
import app.pachli.util.getFormattedDescription
import app.pachli.util.getRelativeTimeSpanString
import app.pachli.util.loadAvatar
import app.pachli.util.setClickableMentions
import app.pachli.util.setClickableText
import app.pachli.view.MediaPreviewImageView
import app.pachli.view.MediaPreviewLayout
import app.pachli.view.PollView
import app.pachli.view.PreviewCardView
import app.pachli.viewdata.PollViewData.Companion.from
import app.pachli.viewdata.StatusViewData
import at.connyduck.sparkbutton.SparkButton
import at.connyduck.sparkbutton.helpers.Utils
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import java.text.NumberFormat
import java.util.Date

abstract class StatusBaseViewHolder protected constructor(itemView: View) :
    RecyclerView.ViewHolder(itemView) {
    object Key {
        const val KEY_CREATED = "created"
    }

    private val displayName: TextView
    private val username: TextView
    private val replyButton: ImageButton
    private val replyCountLabel: TextView?
    private val reblogButton: SparkButton?
    private val favouriteButton: SparkButton
    private val bookmarkButton: SparkButton
    private val moreButton: ImageButton
    private val mediaContainer: ConstraintLayout
    protected val mediaPreview: MediaPreviewLayout
    private val sensitiveMediaWarning: TextView
    private val sensitiveMediaShow: View
    protected val mediaLabels: Array<TextView>
    private val mediaDescriptions: Array<CharSequence?>
    private val contentWarningButton: MaterialButton
    private val avatarInset: ImageView
    val avatar: ImageView
    val metaInfo: TextView
    val content: TextView
    private val contentWarningDescription: TextView
    private val pollView: PollView
    private val cardView: PreviewCardView?
    private val filteredPlaceholder: LinearLayout?
    private val filteredPlaceholderLabel: TextView?
    private val filteredPlaceholderShowButton: Button?
    private val statusContainer: ConstraintLayout?
    private val numberFormat = NumberFormat.getNumberInstance()
    private val absoluteTimeFormatter = AbsoluteTimeFormatter()
    protected val avatarRadius48dp: Int
    private val avatarRadius36dp: Int
    private val avatarRadius24dp: Int
    private val mediaPreviewUnloaded: Drawable

    init {
        displayName = itemView.findViewById(R.id.status_display_name)
        username = itemView.findViewById(R.id.status_username)
        metaInfo = itemView.findViewById(R.id.status_meta_info)
        content = itemView.findViewById(R.id.status_content)
        avatar = itemView.findViewById(R.id.status_avatar)
        replyButton = itemView.findViewById(R.id.status_reply)
        replyCountLabel = itemView.findViewById(R.id.status_replies)
        reblogButton = itemView.findViewById(R.id.status_inset)
        favouriteButton = itemView.findViewById(R.id.status_favourite)
        bookmarkButton = itemView.findViewById(R.id.status_bookmark)
        moreButton = itemView.findViewById(R.id.status_more)
        mediaContainer = itemView.findViewById(R.id.status_media_preview_container)
        mediaContainer.clipToOutline = true
        mediaPreview = itemView.findViewById(R.id.status_media_preview)
        sensitiveMediaWarning = itemView.findViewById(R.id.status_sensitive_media_warning)
        sensitiveMediaShow = itemView.findViewById(R.id.status_sensitive_media_button)
        mediaLabels = arrayOf(
            itemView.findViewById(R.id.status_media_label_0),
            itemView.findViewById(R.id.status_media_label_1),
            itemView.findViewById(R.id.status_media_label_2),
            itemView.findViewById(R.id.status_media_label_3),
        )
        mediaDescriptions = arrayOfNulls(mediaLabels.size)
        contentWarningDescription = itemView.findViewById(R.id.status_content_warning_description)
        contentWarningButton = itemView.findViewById(R.id.status_content_warning_button)
        avatarInset = itemView.findViewById(R.id.status_avatar_inset)
        pollView = itemView.findViewById(R.id.status_poll)
        cardView = itemView.findViewById(R.id.status_card_view)
        filteredPlaceholder = itemView.findViewById(R.id.status_filtered_placeholder)
        filteredPlaceholderLabel = itemView.findViewById(R.id.status_filter_label)
        filteredPlaceholderShowButton = itemView.findViewById(R.id.status_filter_show_anyway)
        statusContainer = itemView.findViewById(R.id.status_container)
        avatarRadius48dp =
            itemView.context.resources.getDimensionPixelSize(R.dimen.avatar_radius_48dp)
        avatarRadius36dp =
            itemView.context.resources.getDimensionPixelSize(R.dimen.avatar_radius_36dp)
        avatarRadius24dp =
            itemView.context.resources.getDimensionPixelSize(R.dimen.avatar_radius_24dp)
        mediaPreviewUnloaded =
            ColorDrawable(MaterialColors.getColor(itemView, android.R.attr.textColorLink))
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
        val emojifiedName =
            name.emojify(customEmojis, displayName, statusDisplayOptions.animateEmojis)
        displayName.text = emojifiedName
    }

    protected fun setUsername(name: String) {
        val context = username.context
        val usernameText = context.getString(R.string.post_username_format, name)
        username.text = usernameText
    }

    fun toggleContentWarning() {
        contentWarningButton.performClick()
    }

    protected fun setSpoilerAndContent(
        status: StatusViewData,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener,
    ) {
        val (_, _, _, _, _, _, _, _, _, emojis) = status.actionable
        val spoilerText = status.spoilerText
        val sensitive = !TextUtils.isEmpty(spoilerText)
        val expanded = status.isExpanded
        if (sensitive) {
            val emojiSpoiler = spoilerText.emojify(
                emojis,
                contentWarningDescription,
                statusDisplayOptions.animateEmojis,
            )
            contentWarningDescription.text = emojiSpoiler
            contentWarningDescription.visibility = View.VISIBLE
            contentWarningButton.visibility = View.VISIBLE
            setContentWarningButtonText(expanded)
            contentWarningButton.setOnClickListener {
                toggleExpandedState(
                    true,
                    !expanded,
                    status,
                    statusDisplayOptions,
                    listener,
                )
            }
            setTextVisible(true, expanded, status, statusDisplayOptions, listener)
        } else {
            contentWarningDescription.visibility = View.GONE
            contentWarningButton.visibility = View.GONE
            setTextVisible(
                sensitive = false,
                expanded = true,
                status = status,
                statusDisplayOptions = statusDisplayOptions,
                listener = listener,
            )
        }
    }

    private fun setContentWarningButtonText(expanded: Boolean) {
        if (expanded) {
            contentWarningButton.setText(R.string.post_content_warning_show_less)
        } else {
            contentWarningButton.setText(R.string.post_content_warning_show_more)
        }
    }

    protected open fun toggleExpandedState(
        sensitive: Boolean,
        expanded: Boolean,
        status: StatusViewData,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener,
    ) {
        contentWarningDescription.invalidate()
        val adapterPosition = bindingAdapterPosition
        if (adapterPosition != RecyclerView.NO_POSITION) {
            listener.onExpandedChange(expanded, adapterPosition)
        }
        setContentWarningButtonText(expanded)
        setTextVisible(sensitive, expanded, status, statusDisplayOptions, listener)
        setupCard(
            status,
            expanded,
            statusDisplayOptions.cardViewMode,
            statusDisplayOptions,
            listener,
        )
    }

    private fun setTextVisible(
        sensitive: Boolean,
        expanded: Boolean,
        status: StatusViewData,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener,
    ) {
        val (_, _, _, _, _, _, _, _, _, emojis, _, _, _, _, _, _, _, _, _, _, mentions, tags, _, _, _, poll) = status.actionable
        val content = status.content
        if (expanded) {
            val emojifiedText =
                content.emojify(emojis, this.content, statusDisplayOptions.animateEmojis)
            setClickableText(this.content, emojifiedText, mentions, tags, listener)
            for (i in mediaLabels.indices) {
                updateMediaLabel(i, sensitive, true)
            }

            poll?.let {
                pollView.bind(
                    from(it),
                    emojis,
                    statusDisplayOptions,
                    numberFormat,
                    absoluteTimeFormatter
                ) { choices ->
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        choices?.let { listener.onVoteInPoll(position, it) }
                            ?: listener.onViewThread(position)
                    }
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
                Glide.with(avatarInset)
                    .load(R.drawable.bot_badge)
                    .into(avatarInset)
            } else {
                avatarInset.visibility = View.GONE
            }
            avatarRadius = avatarRadius48dp
        } else {
            val padding = Utils.dpToPx(avatar.context, 12)
            avatar.setPaddingRelative(0, 0, padding, padding)
            avatarInset.visibility = View.VISIBLE
            avatarInset.background = null
            loadAvatar(
                rebloggedUrl,
                avatarInset,
                avatarRadius24dp,
                statusDisplayOptions.animateAvatars,
                null,
            )
            avatarRadius = avatarRadius36dp
        }
        loadAvatar(
            url,
            avatar,
            avatarRadius,
            statusDisplayOptions.animateAvatars,
            listOf(CompositeWithOpaqueBackground(avatar)),
        )
    }

    protected open fun setMetaData(
        statusViewData: StatusViewData,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener,
    ) {
        val (_, _, _, _, _, _, _, createdAt, editedAt) = statusViewData.actionable
        var timestampText: String
        timestampText = if (statusDisplayOptions.useAbsoluteTime) {
            absoluteTimeFormatter.format(createdAt, true)
        } else {
            val then = createdAt.time
            val now = System.currentTimeMillis()
            getRelativeTimeSpanString(metaInfo.context, then, now)
        }
        editedAt?.let { timestampText = metaInfo.context.getString(
                R.string.post_timestamp_with_edited_indicator,
                timestampText,
            ) }
        metaInfo.text = timestampText
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
            if (createdAt == null) {
                "? minutes"
            } else {
                val then = createdAt.time
                val now = System.currentTimeMillis()
                DateUtils.getRelativeTimeSpanString(
                    then,
                    now,
                    DateUtils.SECOND_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE,
                )
            }
        }
    }

    protected fun setIsReply(isReply: Boolean) {
        if (isReply) {
            replyButton.setImageResource(R.drawable.ic_reply_all_24dp)
        } else {
            replyButton.setImageResource(R.drawable.ic_reply_24dp)
        }
    }

    private fun setReplyCount(repliesCount: Int, fullStats: Boolean) {
        // This label only exists in the non-detailed view (to match the web ui)
        if (replyCountLabel == null) return
        if (fullStats) {
            replyCountLabel.text = formatNumber(repliesCount.toLong(), 1000)
            return
        }

        // Show "0", "1", or "1+" for replies otherwise, so the user knows if there is a thread
        // that they can click through to read.
        replyCountLabel.text =
            if (repliesCount > 1) replyCountLabel.context.getString(R.string.status_count_one_plus) else repliesCount.toString()
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
        } else {
            val disabledId: Int = if (visibility === Status.Visibility.DIRECT) {
                R.drawable.ic_reblog_direct_24dp
            } else {
                R.drawable.ic_reblog_private_24dp
            }
            reblogButton.setInactiveImage(disabledId)
            reblogButton.setActiveImage(disabledId)
        }
    }

    protected fun setFavourited(favourited: Boolean) {
        favouriteButton.isChecked = favourited
    }

    protected fun setBookmarked(bookmarked: Boolean) {
        bookmarkButton.isChecked = bookmarked
    }

    private fun decodeBlurHash(blurhash: String): BitmapDrawable {
        return decodeBlurHash(avatar.context, blurhash)
    }

    private fun loadImage(
        imageView: MediaPreviewImageView,
        previewUrl: String?,
        meta: Attachment.MetaData?,
        blurhash: String?,
    ) {
        val placeholder = blurhash?.let { decodeBlurHash(it) } ?: mediaPreviewUnloaded
        if (TextUtils.isEmpty(previewUrl)) {
            imageView.removeFocalPoint()
            Glide.with(imageView)
                .load(placeholder)
                .centerInside()
                .into(imageView)
        } else {
            val focus = meta?.focus
            if (focus != null) { // If there is a focal point for this attachment:
                imageView.setFocalPoint(focus)
                Glide.with(imageView.context)
                    .load(previewUrl)
                    .placeholder(placeholder)
                    .centerInside()
                    .addListener(imageView)
                    .into(imageView)
            } else {
                imageView.removeFocalPoint()
                Glide.with(imageView)
                    .load(previewUrl)
                    .placeholder(placeholder)
                    .centerInside()
                    .into(imageView)
            }
        }
    }

    protected fun setMediaPreviews(
        attachments: List<Attachment>,
        sensitive: Boolean,
        listener: StatusActionListener,
        showingContent: Boolean,
        useBlurhash: Boolean,
    ) {
        mediaPreview.visibility = View.VISIBLE
        mediaPreview.aspectRatios = attachments.aspectRatios()
        mediaPreview.forEachIndexed { i: Int, imageView: MediaPreviewImageView, descriptionIndicator: TextView ->
            val attachment = attachments[i]
            val previewUrl = attachment.previewUrl
            val description = attachment.description
            val hasDescription = !TextUtils.isEmpty(description)
            if (hasDescription) {
                imageView.contentDescription = description
            } else {
                imageView.contentDescription =
                    imageView.context.getString(R.string.action_view_media)
            }
            loadImage(
                imageView,
                if (showingContent) previewUrl else null,
                attachment.meta,
                if (useBlurhash) attachment.blurhash else null,
            )
            val type = attachment.type
            if (showingContent && (type === Attachment.Type.VIDEO || type === Attachment.Type.GIFV)) {
                imageView.foreground =
                    ContextCompat.getDrawable(itemView.context, R.drawable.play_indicator_overlay)
            } else {
                imageView.foreground = null
            }
            setAttachmentClickListener(imageView, listener, i, attachment, true)
            if (sensitive) {
                sensitiveMediaWarning.setText(R.string.post_sensitive_media_title)
            } else {
                sensitiveMediaWarning.setText(R.string.post_media_hidden_title)
            }
            sensitiveMediaWarning.visibility = if (showingContent) View.GONE else View.VISIBLE
            sensitiveMediaShow.visibility = if (showingContent) View.VISIBLE else View.GONE
            descriptionIndicator.visibility =
                if (hasDescription && showingContent) View.VISIBLE else View.GONE
            sensitiveMediaShow.setOnClickListener { v: View ->
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    listener.onContentHiddenChange(false, bindingAdapterPosition)
                }
                v.visibility = View.GONE
                sensitiveMediaWarning.visibility = View.VISIBLE
                descriptionIndicator.visibility = View.GONE
            }
            sensitiveMediaWarning.setOnClickListener { v: View ->
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    listener.onContentHiddenChange(true, bindingAdapterPosition)
                }
                v.visibility = View.GONE
                sensitiveMediaShow.visibility = View.VISIBLE
                descriptionIndicator.visibility = if (hasDescription) View.VISIBLE else View.GONE
            }
        }
    }

    private fun updateMediaLabel(index: Int, sensitive: Boolean, showingContent: Boolean) {
        val context = itemView.context
        val label =
            if (sensitive && !showingContent) context.getString(R.string.post_sensitive_media_title) else mediaDescriptions[index]
        mediaLabels[index].text = label
    }

    protected fun setMediaLabel(
        attachments: List<Attachment>,
        sensitive: Boolean,
        listener: StatusActionListener,
        showingContent: Boolean,
    ) {
        val context = itemView.context
        for (i in mediaLabels.indices) {
            val mediaLabel = mediaLabels[i]
            if (i < attachments.size) {
                val attachment = attachments[i]
                mediaLabel.visibility = View.VISIBLE
                mediaDescriptions[i] = attachment.getFormattedDescription(context)
                updateMediaLabel(i, sensitive, showingContent)

                // Set the icon next to the label.
                val drawableId = attachments[0].iconResource()
                mediaLabel.setCompoundDrawablesWithIntrinsicBounds(drawableId, 0, 0, 0)
                setAttachmentClickListener(mediaLabel, listener, i, attachment, false)
            } else {
                mediaLabel.visibility = View.GONE
            }
        }
    }

    private fun setAttachmentClickListener(
        view: View,
        listener: StatusActionListener,
        index: Int,
        attachment: Attachment,
        animateTransition: Boolean,
    ) {
        view.setOnClickListener { v: View? ->
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                if (sensitiveMediaWarning.visibility == View.VISIBLE) {
                    listener.onContentHiddenChange(true, bindingAdapterPosition)
                } else {
                    listener.onViewMedia(position, index, if (animateTransition) v else null)
                }
            }
        }
        view.setOnLongClickListener {
            val description = attachment.getFormattedDescription(view.context)
            Toast.makeText(view.context, description, Toast.LENGTH_LONG).show()
            true
        }
    }

    protected fun hideSensitiveMediaWarning() {
        sensitiveMediaWarning.visibility = View.GONE
        sensitiveMediaShow.visibility = View.GONE
    }

    protected fun setupButtons(
        listener: StatusActionListener,
        accountId: String,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        val profileButtonClickListener =
            View.OnClickListener { listener.onViewAccount(accountId) }
        avatar.setOnClickListener(profileButtonClickListener)
        displayName.setOnClickListener(profileButtonClickListener)
        replyButton.setOnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onReply(position)
            }
        }
        reblogButton?.setEventListener { _: SparkButton?, buttonState: Boolean ->
            // return true to play animation
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                if (statusDisplayOptions.confirmReblogs) {
                    showConfirmReblog(listener, buttonState, position)
                    return@setEventListener false
                } else {
                    listener.onReblog(!buttonState, position)
                    return@setEventListener true
                }
            } else {
                return@setEventListener false
            }
        }
        favouriteButton.setEventListener { _: SparkButton?, buttonState: Boolean ->
            // return true to play animation
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                if (statusDisplayOptions.confirmFavourites) {
                    showConfirmFavourite(listener, buttonState, position)
                    return@setEventListener false
                } else {
                    listener.onFavourite(!buttonState, position)
                    return@setEventListener true
                }
            } else {
                return@setEventListener true
            }
        }
        bookmarkButton.setEventListener { _: SparkButton?, buttonState: Boolean ->
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onBookmark(!buttonState, position)
            }
            true
        }
        moreButton.setOnClickListener { v: View? ->
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onMore(v!!, position)
            }
        }
        /* Even though the content TextView is a child of the container, it won't respond to clicks
         * if it contains URLSpans without also setting its listener. The surrounding spans will
         * just eat the clicks instead of deferring to the parent listener, but WILL respond to a
         * listener directly on the TextView, for whatever reason. */
        val viewThreadListener = View.OnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onViewThread(position)
            }
        }
        content.setOnClickListener(viewThreadListener)
        itemView.setOnClickListener(viewThreadListener)
    }

    private fun showConfirmReblog(
        listener: StatusActionListener,
        buttonState: Boolean,
        position: Int,
    ) {
        val popup = PopupMenu(itemView.context, reblogButton!!)
        popup.inflate(R.menu.status_reblog)
        val menu = popup.menu
        if (buttonState) {
            menu.findItem(R.id.menu_action_reblog).isVisible = false
        } else {
            menu.findItem(R.id.menu_action_unreblog).isVisible = false
        }
        popup.setOnMenuItemClickListener {
            listener.onReblog(!buttonState, position)
            if (!buttonState) {
                reblogButton.playAnimation()
            }
            true
        }
        popup.show()
    }

    private fun showConfirmFavourite(
        listener: StatusActionListener,
        buttonState: Boolean,
        position: Int,
    ) {
        val popup = PopupMenu(itemView.context, favouriteButton)
        popup.inflate(R.menu.status_favourite)
        val menu = popup.menu
        if (buttonState) {
            menu.findItem(R.id.menu_action_favourite).isVisible = false
        } else {
            menu.findItem(R.id.menu_action_unfavourite).isVisible = false
        }
        popup.setOnMenuItemClickListener {
            listener.onFavourite(!buttonState, position)
            if (!buttonState) {
                favouriteButton.playAnimation()
            }
            true
        }
        popup.show()
    }

    fun setupWithStatus(
        status: StatusViewData,
        listener: StatusActionListener,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        this.setupWithStatus(status, listener, statusDisplayOptions, null)
    }

    open fun setupWithStatus(
        status: StatusViewData,
        listener: StatusActionListener,
        statusDisplayOptions: StatusDisplayOptions,
        payloads: Any?,
    ) {
        if (payloads == null) {
            val actionable = status.actionable
            setDisplayName(actionable.account.name, actionable.account.emojis, statusDisplayOptions)
            setUsername(status.username)
            setMetaData(status, statusDisplayOptions, listener)
            setIsReply(actionable.inReplyToId != null)
            setReplyCount(actionable.repliesCount, statusDisplayOptions.showStatsInline)
            setAvatar(
                actionable.account.avatar,
                status.rebloggedAvatar,
                actionable.account.bot,
                statusDisplayOptions,
            )
            setReblogged(actionable.reblogged)
            setFavourited(actionable.favourited)
            setBookmarked(actionable.bookmarked)
            val attachments = actionable.attachments
            val sensitive = actionable.sensitive
            if (statusDisplayOptions.mediaPreviewEnabled && hasPreviewableAttachment(attachments)) {
                setMediaPreviews(
                    attachments,
                    sensitive,
                    listener,
                    status.isShowingContent,
                    statusDisplayOptions.useBlurhash,
                )
                if (attachments.isEmpty()) {
                    hideSensitiveMediaWarning()
                }
                // Hide the unused label.
                for (mediaLabel in mediaLabels) {
                    mediaLabel.visibility = View.GONE
                }
            } else {
                setMediaLabel(attachments, sensitive, listener, status.isShowingContent)
                // Hide all unused views.
                mediaPreview.visibility = View.GONE
                hideSensitiveMediaWarning()
            }
            setupCard(
                status,
                status.isExpanded,
                statusDisplayOptions.cardViewMode,
                statusDisplayOptions,
                listener,
            )
            setupButtons(
                listener,
                actionable.account.id,
                statusDisplayOptions,
            )
            setRebloggingEnabled(actionable.rebloggingAllowed(), actionable.visibility)
            setSpoilerAndContent(status, statusDisplayOptions, listener)
            setDescriptionForStatus(status, statusDisplayOptions)

            // Workaround for RecyclerView 1.0.0 / androidx.core 1.0.0
            // RecyclerView tries to set AccessibilityDelegateCompat to null
            // but ViewCompat code replaces is with the default one. RecyclerView never
            // fetches another one from its delegate because it checks that it's set so we remove it
            // and let RecyclerView ask for a new delegate.
            itemView.accessibilityDelegate = null
        } else {
            if (payloads is List<*>) {
                for (item in payloads) {
                    if (Key.KEY_CREATED == item) {
                        setMetaData(status, statusDisplayOptions, listener)
                    }
                }
            }
        }
    }

    private fun setDescriptionForStatus(
        status: StatusViewData,
        statusDisplayOptions: StatusDisplayOptions,
    ) {
        val context = itemView.context
        val (_, _, account, _, _, _, _, createdAt, editedAt, _, reblogsCount, favouritesCount, _, reblogged, favourited, bookmarked, sensitive, _, visibility) = status.actionable
        val description = context.getString(
            R.string.description_status,
            account.displayName,
            getContentWarningDescription(context, status),
            if (TextUtils.isEmpty(status.spoilerText) || !sensitive || status.isExpanded) status.content else "",
            getCreatedAtDescription(createdAt, statusDisplayOptions),
            editedAt?.let { context.getString(R.string.description_post_edited) } ?: "",
            getReblogDescription(context, status),
            status.username,
            if (reblogged) context.getString(R.string.description_post_reblogged) else "",
            if (favourited) context.getString(R.string.description_post_favourited) else "",
            if (bookmarked) context.getString(R.string.description_post_bookmarked) else "",
            getMediaDescription(context, status),
            visibility.description(context),
            getFavsText(context, favouritesCount),
            getReblogsText(context, reblogsCount),
            status.actionable.poll?.let {
                pollView.getPollDescription(
                    from(it),
                    statusDisplayOptions,
                    numberFormat,
                    absoluteTimeFormatter,
                )
            } ?: ""
        )
        itemView.contentDescription = description
    }

    protected fun getFavsText(context: Context, count: Int): CharSequence {
        return if (count > 0) {
            val countString = numberFormat.format(count.toLong())
            HtmlCompat.fromHtml(
                context.resources.getQuantityString(R.plurals.favs, count, countString),
                HtmlCompat.FROM_HTML_MODE_LEGACY,
            )
        } else {
            ""
        }
    }

    protected fun getReblogsText(context: Context, count: Int): CharSequence {
        return if (count > 0) {
            val countString = numberFormat.format(count.toLong())
            HtmlCompat.fromHtml(
                context.resources.getQuantityString(
                    R.plurals.reblogs,
                    count,
                    countString,
                ),
                HtmlCompat.FROM_HTML_MODE_LEGACY,
            )
        } else {
            ""
        }
    }

    protected fun setupCard(
        status: StatusViewData,
        expanded: Boolean,
        cardViewMode: CardViewMode,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener,
    ) {
        cardView ?: return

        val (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, sensitive, _, _, attachments, _, _, _, _, _, poll, card) = status.actionable
        if (cardViewMode !== CardViewMode.NONE && attachments.isEmpty() && poll == null && card != null &&
            !TextUtils.isEmpty(card.url) &&
            (!sensitive || expanded) &&
            (!status.isCollapsible || !status.isCollapsed)
        ) {
            cardView.visibility = View.VISIBLE
            cardView.bind(card, status.actionable.sensitive, statusDisplayOptions) { target ->
                if (card.kind == PreviewCardKind.PHOTO && card.embedUrl.isNotEmpty() && target == PreviewCardView.Target.IMAGE) {
                    cardView.context.startActivity(
                        newSingleImageIntent(cardView.context, card.embedUrl)
                    )
                } else {
                    listener.onViewUrl(card.url)
                }
            }
        } else {
            cardView.visibility = View.GONE
        }
    }

    open fun showStatusContent(show: Boolean) {
        val visibility = if (show) View.VISIBLE else View.GONE
        avatar.visibility = visibility
        avatarInset.visibility = visibility
        displayName.visibility = visibility
        username.visibility = visibility
        metaInfo.visibility = visibility
        contentWarningDescription.visibility = visibility
        contentWarningButton.visibility = visibility
        content.visibility = visibility
        cardView!!.visibility = visibility
        mediaContainer.visibility = visibility
        pollView.visibility = visibility
        replyButton.visibility = visibility
        reblogButton!!.visibility = visibility
        favouriteButton.visibility = visibility
        bookmarkButton.visibility = visibility
        moreButton.visibility = visibility
    }

    companion object {
        private const val TAG = "StatusBaseViewHolder"

        @JvmStatic
        protected fun hasPreviewableAttachment(attachments: List<Attachment>): Boolean {
            for ((_, _, _, _, type) in attachments) {
                if (type === Attachment.Type.AUDIO || type === Attachment.Type.UNKNOWN) {
                    return false
                }
            }
            return true
        }

        private fun getReblogDescription(
            context: Context,
            status: StatusViewData,
        ): CharSequence {
            val reblog = status.rebloggingStatus
            return if (reblog != null) {
                context.getString(R.string.post_boosted_format, reblog.account.username)
            } else {
                ""
            }
        }

        private fun getMediaDescription(
            context: Context,
            status: StatusViewData,
        ): CharSequence {
            if (status.actionable.attachments.isEmpty()) {
                return ""
            }
            val mediaDescriptions =
                status.actionable.attachments.fold(StringBuilder()) { builder: StringBuilder, (_, _, _, _, _, description): Attachment ->
                    if (description == null) {
                        val placeholder =
                            context.getString(R.string.description_post_media_no_description_placeholder)
                        return@fold builder.append(placeholder)
                    } else {
                        builder.append("; ")
                        return@fold builder.append(description)
                    }
                }
            return context.getString(R.string.description_post_media, mediaDescriptions)
        }

        private fun getContentWarningDescription(
            context: Context,
            status: StatusViewData,
        ): CharSequence {
            return if (!TextUtils.isEmpty(status.spoilerText)) {
                context.getString(R.string.description_post_cw, status.spoilerText)
            } else {
                ""
            }
        }
    }
}

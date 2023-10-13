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
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.PopupMenu
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.ViewMediaActivity.Companion.newSingleImageIntent
import app.pachli.entity.Attachment
import app.pachli.entity.Card
import app.pachli.entity.Emoji
import app.pachli.entity.Filter
import app.pachli.entity.Status
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
import app.pachli.util.formatPollDuration
import app.pachli.util.getFormattedDescription
import app.pachli.util.getRelativeTimeSpanString
import app.pachli.util.loadAvatar
import app.pachli.util.setClickableMentions
import app.pachli.util.setClickableText
import app.pachli.view.MediaPreviewImageView
import app.pachli.view.MediaPreviewLayout
import app.pachli.viewdata.PollViewData
import app.pachli.viewdata.PollViewData.Companion.from
import app.pachli.viewdata.StatusViewData
import app.pachli.viewdata.buildDescription
import app.pachli.viewdata.calculatePercent
import at.connyduck.sparkbutton.SparkButton
import at.connyduck.sparkbutton.helpers.Utils
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.ShapeAppearanceModel
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
    private val pollOptions: RecyclerView
    private val pollDescription: TextView
    private val pollButton: Button
    private val cardView: LinearLayout?
    private val cardInfo: LinearLayout
    private val cardImage: ShapeableImageView
    private val cardTitle: TextView
    private val cardDescription: TextView
    private val cardUrl: TextView
    private val pollAdapter: PollAdapter
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
        pollOptions = itemView.findViewById(R.id.status_poll_options)
        pollDescription = itemView.findViewById(R.id.status_poll_description)
        pollButton = itemView.findViewById(R.id.status_poll_button)
        cardView = itemView.findViewById(R.id.status_card_view)
        cardInfo = itemView.findViewById(R.id.card_info)
        cardImage = itemView.findViewById(R.id.card_image)
        cardTitle = itemView.findViewById(R.id.card_title)
        cardDescription = itemView.findViewById(R.id.card_description)
        cardUrl = itemView.findViewById(R.id.card_link)
        filteredPlaceholder = itemView.findViewById(R.id.status_filtered_placeholder)
        filteredPlaceholderLabel = itemView.findViewById(R.id.status_filter_label)
        filteredPlaceholderShowButton = itemView.findViewById(R.id.status_filter_show_anyway)
        statusContainer = itemView.findViewById(R.id.status_container)
        pollAdapter = PollAdapter()
        pollOptions.adapter = pollAdapter
        pollOptions.layoutManager = LinearLayoutManager(pollOptions.context)
        val itemAnimator = pollOptions.itemAnimator as DefaultItemAnimator?
        if (itemAnimator != null) itemAnimator.supportsChangeAnimations = false
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
            if (poll != null) {
                setupPoll(from(poll), emojis, statusDisplayOptions, listener)
            } else {
                hidePoll()
            }
        } else {
            hidePoll()
            setClickableMentions(this.content, mentions, listener)
        }
        if (TextUtils.isEmpty(this.content.text)) {
            this.content.visibility = View.GONE
        } else {
            this.content.visibility = View.VISIBLE
        }
    }

    private fun hidePoll() {
        pollButton.visibility = View.GONE
        pollDescription.visibility = View.GONE
        pollOptions.visibility = View.GONE
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
        if (editedAt != null) {
            timestampText = metaInfo.context.getString(
                R.string.post_timestamp_with_edited_indicator,
                timestampText,
            )
        }
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
                val drawableId = getLabelIcon(attachments[0].type)
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
        statusContent: String?,
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
                status.content.toString(),
                statusDisplayOptions,
            )
            setRebloggingEnabled(actionable.rebloggingAllowed(), actionable.visibility)
            setSpoilerAndContent(status, statusDisplayOptions, listener)
            setupFilterPlaceholder(status, listener, statusDisplayOptions)
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

    private fun setupFilterPlaceholder(
        status: StatusViewData,
        listener: StatusActionListener,
        displayOptions: StatusDisplayOptions,
    ) {
        if (status.filterAction !== Filter.Action.WARN) {
            showFilteredPlaceholder(false)
            return
        }

        // Shouldn't be necessary given the previous test against getFilterAction(),
        // but guards against a possible NPE. See the TODO in StatusViewData.filterAction
        // for more details.
        val filterResults = status.actionable.filtered
        if (filterResults.isNullOrEmpty()) {
            showFilteredPlaceholder(false)
            return
        }
        var matchedFilter: Filter? = null
        for ((filter) in filterResults) {
            if (filter.action === Filter.Action.WARN) {
                matchedFilter = filter
                break
            }
        }

        // Guard against a possible NPE
        if (matchedFilter == null) {
            showFilteredPlaceholder(false)
            return
        }
        showFilteredPlaceholder(true)
        filteredPlaceholderLabel?.text = itemView.context.getString(
            R.string.status_filter_placeholder_label_format,
            matchedFilter.title,
        )
        filteredPlaceholderShowButton?.setOnClickListener {
            listener.clearWarningAction(
                bindingAdapterPosition,
            )
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
            if (editedAt != null) context.getString(R.string.description_post_edited) else "",
            getReblogDescription(context, status),
            status.username,
            if (reblogged) context.getString(R.string.description_post_reblogged) else "",
            if (favourited) context.getString(R.string.description_post_favourited) else "",
            if (bookmarked) context.getString(R.string.description_post_bookmarked) else "",
            getMediaDescription(context, status),
            getVisibilityDescription(context, visibility),
            getFavsText(context, favouritesCount),
            getReblogsText(context, reblogsCount),
            getPollDescription(status, context, statusDisplayOptions),
        )
        itemView.contentDescription = description
    }

    private fun getPollDescription(
        status: StatusViewData,
        context: Context,
        statusDisplayOptions: StatusDisplayOptions,
    ): CharSequence {
        val poll = status.actionable.poll ?: return ""
        val pollViewData = from(poll)
        val args: Array<CharSequence?> = arrayOfNulls(5)
        val options = pollViewData.options
        val totalVotes = pollViewData.votesCount
        val totalVoters = pollViewData.votersCount
        for (i in args.indices) {
            if (i < options.size) {
                val percent = calculatePercent(options[i].votesCount, totalVoters, totalVotes)
                args[i] = buildDescription(options[i].title, percent, options[i].voted, context)
            } else {
                args[i] = ""
            }
        }
        args[4] = getPollInfoText(
            System.currentTimeMillis(),
            pollViewData,
            statusDisplayOptions,
            context,
        )
        return context.getString(R.string.description_poll, *args)
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

    private fun setupPoll(
        poll: PollViewData,
        emojis: List<Emoji>,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener,
    ) {
        val timestamp = System.currentTimeMillis()
        val expired = poll.expired || poll.expiresAt != null && timestamp > poll.expiresAt.time
        val context = pollDescription.context
        pollOptions.visibility = View.VISIBLE
        if (expired || poll.voted) {
            // no voting possible
            val viewThreadListener = View.OnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    listener.onViewThread(position)
                }
            }
            pollAdapter.setup(
                poll.options,
                poll.votesCount,
                poll.votersCount,
                emojis,
                PollAdapter.RESULT,
                viewThreadListener,
                statusDisplayOptions.animateEmojis,
            )
            pollButton.visibility = View.GONE
        } else {
            // voting possible
            val optionClickListener = View.OnClickListener {
                pollButton.isEnabled = pollAdapter.getSelected().isNotEmpty()
            }
            pollAdapter.setup(
                poll.options,
                poll.votesCount,
                poll.votersCount,
                emojis,
                if (poll.multiple) PollAdapter.MULTIPLE else PollAdapter.SINGLE,
                null,
                statusDisplayOptions.animateEmojis,
                true,
                optionClickListener,
            )
            pollButton.visibility = View.VISIBLE
            pollButton.isEnabled = false
            pollButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val pollResult = pollAdapter.getSelected()
                    if (pollResult.isNotEmpty()) {
                        listener.onVoteInPoll(position, pollResult)
                    }
                }
            }
        }
        pollDescription.visibility = View.VISIBLE
        pollDescription.text = getPollInfoText(timestamp, poll, statusDisplayOptions, context)
    }

    private fun getPollInfoText(
        timestamp: Long,
        poll: PollViewData,
        statusDisplayOptions: StatusDisplayOptions,
        context: Context,
    ): CharSequence {
        val votesText: String = if (poll.votersCount == null) {
            val voters = numberFormat.format(poll.votesCount.toLong())
            context.resources.getQuantityString(R.plurals.poll_info_votes, poll.votesCount, voters)
        } else {
            val voters = numberFormat.format(poll.votersCount)
            context.resources.getQuantityString(
                R.plurals.poll_info_people,
                poll.votersCount,
                voters,
            )
        }
        val pollDurationInfo: CharSequence = if (poll.expired) {
            context.getString(R.string.poll_info_closed)
        } else if (poll.expiresAt == null) {
            return votesText
        } else {
            if (statusDisplayOptions.useAbsoluteTime) {
                context.getString(
                    R.string.poll_info_time_absolute,
                    absoluteTimeFormatter.format(poll.expiresAt, false),
                )
            } else {
                formatPollDuration(pollDescription.context, poll.expiresAt.time, timestamp)
            }
        }
        return pollDescription.context.getString(
            R.string.poll_info_format,
            votesText,
            pollDurationInfo,
        )
    }

    protected fun setupCard(
        status: StatusViewData,
        expanded: Boolean,
        cardViewMode: CardViewMode,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener,
    ) {
        if (cardView == null) {
            return
        }
        val (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, sensitive, _, _, attachments, _, _, _, _, _, poll, card) = status.actionable
        if (cardViewMode !== CardViewMode.NONE && attachments.isEmpty() && poll == null && card != null &&
            !TextUtils.isEmpty(card.url) &&
            (!sensitive || expanded) &&
            (!status.isCollapsible || !status.isCollapsed)
        ) {
            cardView.visibility = View.VISIBLE
            cardTitle.text = card.title
            if (TextUtils.isEmpty(card.description) && TextUtils.isEmpty(card.authorName)) {
                cardDescription.visibility = View.GONE
            } else {
                cardDescription.visibility = View.VISIBLE
                if (TextUtils.isEmpty(card.description)) {
                    cardDescription.text = card.authorName
                } else {
                    cardDescription.text = card.description
                }
            }
            cardUrl.text = card.url

            // Statuses from other activitypub sources can be marked sensitive even if there's no media,
            // so let's blur the preview in that case
            // If media previews are disabled, show placeholder for cards as well
            if (statusDisplayOptions.mediaPreviewEnabled && !sensitive && !TextUtils.isEmpty(card.image)) {
                val radius = cardImage.context.resources
                    .getDimensionPixelSize(R.dimen.card_radius)
                val cardImageShape = ShapeAppearanceModel.builder()
                if (card.width > card.height) {
                    cardView.orientation = LinearLayout.VERTICAL
                    cardImage.layoutParams.height = cardImage.context.resources
                        .getDimensionPixelSize(R.dimen.card_image_vertical_height)
                    cardImage.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    cardInfo.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    cardInfo.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
                    cardImageShape.setTopLeftCorner(CornerFamily.ROUNDED, radius.toFloat())
                    cardImageShape.setTopRightCorner(CornerFamily.ROUNDED, radius.toFloat())
                } else {
                    cardView.orientation = LinearLayout.HORIZONTAL
                    cardImage.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    cardImage.layoutParams.width = cardImage.context.resources
                        .getDimensionPixelSize(R.dimen.card_image_horizontal_width)
                    cardInfo.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    cardInfo.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    cardImageShape.setTopLeftCorner(CornerFamily.ROUNDED, radius.toFloat())
                    cardImageShape.setBottomLeftCorner(CornerFamily.ROUNDED, radius.toFloat())
                }
                cardImage.shapeAppearanceModel = cardImageShape.build()
                cardImage.scaleType = ImageView.ScaleType.CENTER_CROP
                var builder = Glide.with(cardImage.context)
                    .load(card.image)
                    .dontTransform()
                if (statusDisplayOptions.useBlurhash && !TextUtils.isEmpty(card.blurhash)) {
                    builder = builder.placeholder(decodeBlurHash(card.blurhash!!))
                }
                builder.into(cardImage)
            } else if (statusDisplayOptions.useBlurhash && !TextUtils.isEmpty(card.blurhash)) {
                val radius = cardImage.context.resources
                    .getDimensionPixelSize(R.dimen.card_radius)
                cardView.orientation = LinearLayout.HORIZONTAL
                cardImage.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                cardImage.layoutParams.width = cardImage.context.resources
                    .getDimensionPixelSize(R.dimen.card_image_horizontal_width)
                cardInfo.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                cardInfo.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                val cardImageShape = ShapeAppearanceModel.builder()
                    .setTopLeftCorner(CornerFamily.ROUNDED, radius.toFloat())
                    .setBottomLeftCorner(CornerFamily.ROUNDED, radius.toFloat())
                    .build()
                cardImage.shapeAppearanceModel = cardImageShape
                cardImage.scaleType = ImageView.ScaleType.CENTER_CROP
                Glide.with(cardImage.context)
                    .load(decodeBlurHash(card.blurhash!!))
                    .dontTransform()
                    .into(cardImage)
            } else {
                cardView.orientation = LinearLayout.HORIZONTAL
                cardImage.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                cardImage.layoutParams.width = cardImage.context.resources
                    .getDimensionPixelSize(R.dimen.card_image_horizontal_width)
                cardInfo.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                cardInfo.layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                cardImage.shapeAppearanceModel = ShapeAppearanceModel()
                cardImage.scaleType = ImageView.ScaleType.CENTER
                Glide.with(cardImage.context)
                    .load(R.drawable.card_image_placeholder)
                    .into(cardImage)
            }
            val visitLink = View.OnClickListener { listener.onViewUrl(card.url) }
            cardView.setOnClickListener(visitLink)
            // View embedded photos in our image viewer instead of opening the browser
            cardImage.setOnClickListener(
                if (card.type == Card.TYPE_PHOTO && !TextUtils.isEmpty(card.embedUrl)) {
                    View.OnClickListener {
                        cardView.context.startActivity(
                            newSingleImageIntent(cardView.context, card.embedUrl!!),
                        )
                    }
                } else {
                    visitLink
                },
            )
            cardView.clipToOutline = true
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
        pollOptions.visibility = visibility
        pollButton.visibility = visibility
        pollDescription.visibility = visibility
        replyButton.visibility = visibility
        reblogButton!!.visibility = visibility
        favouriteButton.visibility = visibility
        bookmarkButton.visibility = visibility
        moreButton.visibility = visibility
    }

    private fun showFilteredPlaceholder(show: Boolean) {
        if (statusContainer != null) {
            statusContainer.visibility = if (show) View.GONE else View.VISIBLE
        }
        if (filteredPlaceholder != null) {
            filteredPlaceholder.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    companion object {
        private const val TAG = "StatusBaseViewHolder"

        @DrawableRes
        private fun getLabelIcon(type: Attachment.Type): Int {
            return when (type) {
                Attachment.Type.IMAGE -> R.drawable.ic_photo_24dp
                Attachment.Type.GIFV, Attachment.Type.VIDEO -> R.drawable.ic_videocam_24dp
                Attachment.Type.AUDIO -> R.drawable.ic_music_box_24dp
                else -> R.drawable.ic_attach_file_24dp
            }
        }

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
                context
                    .getString(R.string.post_boosted_format, reblog.account.username)
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

        @JvmStatic
        protected fun getVisibilityDescription(
            context: Context,
            visibility: Status.Visibility?,
        ): CharSequence {
            if (visibility == null) {
                return ""
            }
            val resource: Int = when (visibility) {
                Status.Visibility.PUBLIC -> R.string.description_visibility_public
                Status.Visibility.UNLISTED -> R.string.description_visibility_unlisted
                Status.Visibility.PRIVATE -> R.string.description_visibility_private
                Status.Visibility.DIRECT -> R.string.description_visibility_direct
                else -> {
                    return ""
                }
            }
            return context.getString(resource)
        }
    }
}

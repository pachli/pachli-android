package app.pachli.util

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Checkable
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.adapter.FilterableStatusViewHolder
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.core.activity.OpenUrlUseCase
import app.pachli.core.data.model.IStatusViewData
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.AttachmentDisplayReason
import app.pachli.core.model.Status.Companion.MAX_MEDIA_ATTACHMENTS
import app.pachli.core.network.parseAsMastodonHtml
import app.pachli.core.ui.StatusActionListener
import app.pachli.core.ui.StatusControlView
import app.pachli.core.ui.accessibility.PachliRecyclerViewAccessibilityDelegate
import kotlin.math.min

// Not using lambdas because there's boxing of int then
fun interface StatusProvider<T> {
    fun getStatus(pos: Int): T?
}

class ListStatusAccessibilityDelegate<T : IStatusViewData>(
    private val pachliAccountId: Long,
    private val recyclerView: RecyclerView,
    private val statusActionListener: StatusActionListener<T>,
    private val openUrl: OpenUrlUseCase,
    private val statusProvider: StatusProvider<T>,
) : PachliRecyclerViewAccessibilityDelegate(recyclerView) {
    override fun getItemDelegate(): AccessibilityDelegateCompat = itemDelegate

    private val itemDelegate = object : ItemDelegate(this) {
        override fun onInitializeAccessibilityNodeInfo(
            host: View,
            info: AccessibilityNodeInfoCompat,
        ) {
            super.onInitializeAccessibilityNodeInfo(host, info)

            val viewHolder = recyclerView.findContainingViewHolder(host)
            if (viewHolder is FilterableStatusViewHolder<*> && viewHolder.matchedFilter != null) {
                info.addAction(showAnywayAction)
                info.addAction(editFilterAction)
                return
            }

            val pos = recyclerView.getChildAdapterPosition(host)
            val status = statusProvider.getStatus(pos) ?: return

            val actionable = status.actionable
            if (actionable.spoilerText.isNotEmpty()) {
                info.addAction(if (status.isExpanded) collapseCwAction else expandCwAction)
            }

            // Selecting / deselecting statuses when reporting (or any other Checkable
            // viewholder).
            if (viewHolder is Checkable) {
                info.addAction(if (viewHolder.isChecked) unselectStatusAction else selectStatusAction)
            }

            // Hack. Not all statuses are displayed with controls. If it is then fetch
            // extra actions from the control, if present.
            //
            // TODO: Figure out a cleaner way to do this.
            val statusControlView = (viewHolder?.itemView as? ViewGroup)?.children?.firstNotNullOfOrNull { it as? StatusControlView }
            val controlActions = statusControlView?.actions.orEmpty()

            if (controlActions.contains(replyAction.id)) info.addAction(replyAction)

            if (actionable.rebloggingAllowed() && controlActions.contains(reblogAction.id)) {
                info.addAction(if (actionable.reblogged) unreblogAction else reblogAction)
            }

            if (controlActions.contains(favouriteAction.id)) info.addAction(if (actionable.favourited) unfavouriteAction else favouriteAction)
            if (controlActions.contains(bookmarkAction.id)) info.addAction(if (actionable.bookmarked) unbookmarkAction else bookmarkAction)

            val attachmentDisplayAction = status.attachmentDisplayAction
            when (attachmentDisplayAction) {
                is AttachmentDisplayAction.Show -> {
                    val mediaActions = intArrayOf(
                        app.pachli.core.ui.R.id.action_open_media_1,
                        app.pachli.core.ui.R.id.action_open_media_2,
                        app.pachli.core.ui.R.id.action_open_media_3,
                        app.pachli.core.ui.R.id.action_open_media_4,
                    )
                    val attachmentCount = min(actionable.attachments.size, MAX_MEDIA_ATTACHMENTS)
                    for (i in 0 until attachmentCount) {
                        info.addAction(
                            AccessibilityActionCompat(
                                mediaActions[i],
                                context.getString(R.string.action_open_media_n, i + 1),
                            ),
                        )
                        info.addAction(hideAttachmentsAction)
                    }
                }

                is AttachmentDisplayAction.Hide -> info.addAction(showAttachmentsAction)
            }

            val parsedContent = status.content.parseAsMastodonHtml()

            info.addAction(openProfileAction)
            if (parsedContent.getLinks().any()) info.addAction(linksAction)

            val mentions = actionable.mentions
            if (mentions.isNotEmpty()) info.addAction(mentionsAction)

            if (parsedContent.getHashtags().any()) info.addAction(hashtagsAction)
            if (!status.status.reblog?.account?.username.isNullOrEmpty()) {
                info.addAction(openRebloggerAction)
            }
            if (actionable.reblogsCount > 0) info.addAction(openRebloggedByAction)
            if (actionable.favouritesCount > 0) info.addAction(openFavsAction)

            status.actionable.card?.authors?.firstOrNull()?.account?.let {
                info.addAction(openBylineAccountAction)
            }

            if (!status.actionable.account.pronouns.isNullOrBlank()) {
                info.addAction(showPronounsAction)
            }

            if (controlActions.contains(moreAction.id)) info.addAction(moreAction)
        }

        override fun performAccessibilityAction(
            host: View,
            action: Int,
            args: Bundle?,
        ): Boolean {
            val pos = recyclerView.getChildAdapterPosition(host)
            val status = statusProvider.getStatus(pos) ?: return false
            when (action) {
                app.pachli.core.ui.R.id.action_reply -> {
                    interrupt()
                    statusActionListener.onReply(status)
                }
                app.pachli.core.ui.R.id.action_favourite -> statusActionListener.onFavourite(status, true)
                app.pachli.core.ui.R.id.action_unfavourite -> statusActionListener.onFavourite(status, false)
                app.pachli.core.ui.R.id.action_bookmark -> statusActionListener.onBookmark(status, true)
                app.pachli.core.ui.R.id.action_unbookmark -> statusActionListener.onBookmark(status, false)
                app.pachli.core.ui.R.id.action_reblog -> statusActionListener.onReblog(status, true)
                app.pachli.core.ui.R.id.action_unreblog -> statusActionListener.onReblog(status, false)
                app.pachli.core.ui.R.id.action_open_profile -> {
                    interrupt()
                    statusActionListener.onViewAccount(status.actionable.account.id)
                }
                app.pachli.core.ui.R.id.action_open_media_1 -> {
                    interrupt()
                    statusActionListener.onViewAttachment(null, status, 0)
                }
                app.pachli.core.ui.R.id.action_open_media_2 -> {
                    interrupt()
                    statusActionListener.onViewAttachment(null, status, 1)
                }
                app.pachli.core.ui.R.id.action_open_media_3 -> {
                    interrupt()
                    statusActionListener.onViewAttachment(null, status, 2)
                }
                app.pachli.core.ui.R.id.action_open_media_4 -> {
                    interrupt()
                    statusActionListener.onViewAttachment(null, status, 3)
                }
                app.pachli.core.ui.R.id.action_expand_cw -> {
                    // Toggling it directly to avoid animations
                    // which cannot be disabled for detailed status for some reason
                    val holder = recyclerView.getChildViewHolder(host) as StatusBaseViewHolder<IStatusViewData>
                    holder.toggleContentWarning()
                    // Stop and restart narrator before it reads old description.
                    // Would be nice if we could *just* read the content here but doesn't seem
                    // to be possible.
                    forceFocus(host)
                }
                app.pachli.core.ui.R.id.action_collapse_cw -> {
                    statusActionListener.onExpandedChange(status, false)
                    interrupt()
                }

                app.pachli.core.ui.R.id.action_links -> {
                    val links = status.content.parseAsMastodonHtml().getLinks()
                    showA11yDialogWithCopyButton(
                        app.pachli.core.ui.R.string.title_links_dialog,
                        links.map { it.url },
                    ) { openUrl(links[it].url) }
                }

                app.pachli.core.ui.R.id.action_mentions -> {
                    val mentions = status.actionable.mentions
                    showA11yDialogWithCopyButton(
                        app.pachli.core.ui.R.string.title_mentions_dialog,
                        mentions.map { "@${it.username}" },
                    ) { statusActionListener.onViewAccount(mentions[it].id) }
                }

                app.pachli.core.ui.R.id.action_hashtags -> {
                    val hashtags = status.content.parseAsMastodonHtml().getHashtags()
                    showA11yDialogWithCopyButton(
                        app.pachli.core.ui.R.string.title_hashtags_dialog,
                        hashtags.map { "#$it" },
                    ) { statusActionListener.onViewTag(hashtags[it].toString()) }
                }

                app.pachli.core.ui.R.id.action_open_reblogger -> {
                    interrupt()
                    statusActionListener.onOpenReblog(status.status)
                }
                app.pachli.core.ui.R.id.action_open_reblogged_by -> {
                    interrupt()
                    statusActionListener.onShowReblogs(status.actionableId)
                }
                app.pachli.core.ui.R.id.action_open_faved_by -> {
                    interrupt()
                    statusActionListener.onShowFavs(status.actionableId)
                }
                app.pachli.core.ui.R.id.action_open_byline_account -> {
                    status.actionable.card?.authors?.firstOrNull()?.account?.let {
                        interrupt()
                        statusActionListener.onViewAccount(it.id)
                    }
                }
                app.pachli.core.ui.R.id.action_more -> {
                    statusActionListener.onMore(host, status)
                }
                app.pachli.core.ui.R.id.action_show_anyway -> statusActionListener.clearContentFilter(status)
                app.pachli.core.ui.R.id.action_edit_filter -> {
                    (recyclerView.findContainingViewHolder(host) as? FilterableStatusViewHolder<*>)?.matchedFilter?.let {
                        statusActionListener.onEditFilterById(pachliAccountId, it.id)
                        return@let true
                    } ?: false
                }
                app.pachli.core.ui.R.id.action_show_attachments -> {
                    statusActionListener.onAttachmentDisplayActionChange(
                        status,
                        AttachmentDisplayAction.Show(status.attachmentDisplayAction as? AttachmentDisplayAction.Hide),
                    )
                }

                app.pachli.core.ui.R.id.action_hide_attachments -> {
                    // The user clicked to hide the attachment. Either they are:
                    //
                    // a. Re-hiding an attachment that was hidden that they decided to show, or
                    // b. Hiding media that wasn't originally hidden.
                    //
                    // If (a) then the new decision is `Show.originalDecision`. If (b) then
                    // then the new decision is UserAction.
                    val newAction = (status.attachmentDisplayAction as? AttachmentDisplayAction.Show)?.originalAction
                        ?: AttachmentDisplayAction.Hide(AttachmentDisplayReason.UserAction)
                    statusActionListener.onAttachmentDisplayActionChange(status, newAction)
                }

                app.pachli.core.ui.R.id.action_select_status -> {
                    (recyclerView.findContainingViewHolder(host) as? Checkable)?.isChecked = true
                }

                app.pachli.core.ui.R.id.action_unselect_status -> {
                    (recyclerView.findContainingViewHolder(host) as? Checkable)?.isChecked = false
                }

                app.pachli.core.ui.R.id.action_show_pronouns -> {
                    val pronouns = status.actionable.account.pronouns?.trim()
                    if (pronouns.isNullOrBlank()) return true
                    val formatted = HtmlCompat.fromHtml(pronouns, FROM_HTML_MODE_LEGACY)
                    Toast.makeText(context, formatted, Toast.LENGTH_LONG).show()
                }

                else -> return super.performAccessibilityAction(host, action, args)
            }
            return true
        }
    }

    private val collapseCwAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_collapse_cw,
        context.getString(app.pachli.core.ui.R.string.post_content_warning_show_less),
    )

    private val expandCwAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_expand_cw,
        context.getString(app.pachli.core.ui.R.string.post_content_warning_show_more),
    )

    private val replyAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_reply,
        context.getString(app.pachli.core.ui.R.string.action_reply),
    )

    private val unreblogAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_unreblog,
        context.getString(app.pachli.core.ui.R.string.action_unreblog),
    )

    private val reblogAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_reblog,
        context.getString(app.pachli.core.ui.R.string.action_reblog),
    )

    private val unfavouriteAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_unfavourite,
        context.getString(app.pachli.core.ui.R.string.action_unfavourite),
    )

    private val favouriteAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_favourite,
        context.getString(app.pachli.core.ui.R.string.action_favourite),
    )

    private val bookmarkAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_bookmark,
        context.getString(app.pachli.core.ui.R.string.action_bookmark),
    )

    private val unbookmarkAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_unbookmark,
        context.getString(app.pachli.core.ui.R.string.action_bookmark),
    )

    private val openProfileAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_open_profile,
        context.getString(app.pachli.core.ui.R.string.action_view_profile),
    )

    private val linksAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_links,
        context.getString(app.pachli.core.ui.R.string.action_links),
    )

    private val mentionsAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_mentions,
        context.getString(app.pachli.core.ui.R.string.action_mentions),
    )

    private val hashtagsAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_hashtags,
        context.getString(app.pachli.core.ui.R.string.action_hashtags),
    )

    private val openRebloggerAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_open_reblogger,
        context.getString(R.string.action_open_reblogger),
    )

    private val openRebloggedByAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_open_reblogged_by,
        context.getString(R.string.action_open_reblogged_by),
    )

    private val openFavsAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_open_faved_by,
        context.getString(R.string.action_open_faved_by),
    )

    private val openBylineAccountAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_open_byline_account,
        context.getString(R.string.action_open_byline_account),
    )

    private val moreAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_more,
        context.getString(app.pachli.core.ui.R.string.action_more),
    )

    private val showAnywayAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_show_anyway,
        context.getString(app.pachli.core.ui.R.string.status_filtered_show_anyway),
    )

    private val editFilterAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_edit_filter,
        context.getString(app.pachli.core.ui.R.string.filter_edit_title),
    )

    private val showAttachmentsAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_show_attachments,
        context.getString(app.pachli.core.ui.R.string.action_show_attachments),
    )

    private val hideAttachmentsAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_hide_attachments,
        context.getString(app.pachli.core.ui.R.string.action_hide_attachments),
    )

    private val selectStatusAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_select_status,
        context.getString(app.pachli.core.ui.R.string.action_select_status),
    )

    private val unselectStatusAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_unselect_status,
        context.getString(app.pachli.core.ui.R.string.action_unselect_status),
    )

    private val showPronounsAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_show_pronouns,
        context.getString(app.pachli.core.ui.R.string.action_show_pronouns),
    )
}

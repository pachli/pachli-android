package app.pachli.util

import android.content.Context
import android.os.Bundle
import android.text.Spannable
import android.text.style.URLSpan
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerViewAccessibilityDelegate
import app.pachli.R
import app.pachli.adapter.FilterableStatusViewHolder
import app.pachli.adapter.StatusBaseViewHolder
import app.pachli.core.activity.openLink
import app.pachli.core.network.model.Status.Companion.MAX_MEDIA_ATTACHMENTS
import app.pachli.interfaces.StatusActionListener
import app.pachli.viewdata.IStatusViewData
import app.pachli.viewdata.NotificationViewData
import kotlin.math.min

// Not using lambdas because there's boxing of int then
fun interface StatusProvider<T> {
    fun getStatus(pos: Int): T?
}

class ListStatusAccessibilityDelegate<T : IStatusViewData>(
    private val pachliAccountId: Long,
    private val recyclerView: RecyclerView,
    private val statusActionListener: StatusActionListener<T>,
    private val statusProvider: StatusProvider<T>,
) : RecyclerViewAccessibilityDelegate(recyclerView) {
    private val a11yManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
        as AccessibilityManager

    override fun getItemDelegate(): AccessibilityDelegateCompat = itemDelegate

    private val context: Context get() = recyclerView.context

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

            // Ignore notifications that don't have an associated statusViewData,
            // otherwise the accessors throw IllegalStateException.
            // See https://github.com/pachli/pachli-android/issues/669
            if (status as? NotificationViewData != null) {
                if (status.statusViewData == null) return
            }

            if (status.spoilerText.isNotEmpty()) {
                info.addAction(if (status.isExpanded) collapseCwAction else expandCwAction)
            }

            info.addAction(replyAction)

            val actionable = status.actionable
            if (actionable.rebloggingAllowed()) {
                info.addAction(if (actionable.reblogged) unreblogAction else reblogAction)
            }
            info.addAction(if (actionable.favourited) unfavouriteAction else favouriteAction)
            info.addAction(if (actionable.bookmarked) unbookmarkAction else bookmarkAction)

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
            }

            info.addAction(openProfileAction)
            if (getLinks(status).any()) info.addAction(linksAction)

            val mentions = actionable.mentions
            if (mentions.isNotEmpty()) info.addAction(mentionsAction)

            if (getHashtags(status).any()) info.addAction(hashtagsAction)
            if (!status.status.reblog?.account?.username.isNullOrEmpty()) {
                info.addAction(openRebloggerAction)
            }
            if (actionable.reblogsCount > 0) info.addAction(openRebloggedByAction)
            if (actionable.favouritesCount > 0) info.addAction(openFavsAction)

            status.actionable.card?.authors?.firstOrNull()?.account?.let {
                info.addAction(openBylineAccountAction)
            }

            info.addAction(moreAction)
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
                    statusActionListener.onViewMedia(status, 0, null)
                }
                app.pachli.core.ui.R.id.action_open_media_2 -> {
                    interrupt()
                    statusActionListener.onViewMedia(status, 1, null)
                }
                app.pachli.core.ui.R.id.action_open_media_3 -> {
                    interrupt()
                    statusActionListener.onViewMedia(status, 2, null)
                }
                app.pachli.core.ui.R.id.action_open_media_4 -> {
                    interrupt()
                    statusActionListener.onViewMedia(status, 3, null)
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
                    statusActionListener.onExpandedChange(pachliAccountId, status, false)
                    interrupt()
                }
                app.pachli.core.ui.R.id.action_links -> showLinksDialog(host)
                app.pachli.core.ui.R.id.action_mentions -> showMentionsDialog(host)
                app.pachli.core.ui.R.id.action_hashtags -> showHashtagsDialog(host)
                app.pachli.core.ui.R.id.action_open_reblogger -> {
                    interrupt()
                    statusActionListener.onOpenReblog(status.actionable)
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
                app.pachli.core.ui.R.id.action_show_anyway -> statusActionListener.clearWarningAction(pachliAccountId, status)
                app.pachli.core.ui.R.id.action_edit_filter -> {
                    (recyclerView.findContainingViewHolder(host) as? FilterableStatusViewHolder<*>)?.matchedFilter?.let {
                        statusActionListener.onEditFilterById(pachliAccountId, it.id)
                        return@let true
                    } ?: false
                }

                else -> return super.performAccessibilityAction(host, action, args)
            }
            return true
        }

        private fun showLinksDialog(host: View) {
            val status = getStatus(host) as? IStatusViewData ?: return
            val links = getLinks(status).toList()
            val textLinks = links.map { item -> item.link }
            AlertDialog.Builder(host.context)
                .setTitle(app.pachli.core.ui.R.string.title_links_dialog)
                .setAdapter(
                    ArrayAdapter(
                        host.context,
                        android.R.layout.simple_list_item_1,
                        textLinks,
                    ),
                ) { _, which -> host.context.openLink(links[which].link) }
                .show()
                .let { forceFocus(it.listView) }
        }

        private fun showMentionsDialog(host: View) {
            val status = getStatus(host) as? IStatusViewData ?: return
            val mentions = status.actionable.mentions
            val stringMentions = mentions.map { it.username }
            AlertDialog.Builder(host.context)
                .setTitle(R.string.title_mentions_dialog)
                .setAdapter(
                    ArrayAdapter<CharSequence>(
                        host.context,
                        android.R.layout.simple_list_item_1,
                        stringMentions,
                    ),
                ) { _, which ->
                    statusActionListener.onViewAccount(mentions[which].id)
                }
                .show()
                .let { forceFocus(it.listView) }
        }

        private fun showHashtagsDialog(host: View) {
            val status = getStatus(host) as? IStatusViewData ?: return
            val tags = getHashtags(status).map { it.subSequence(1, it.length) }.toList()
            AlertDialog.Builder(host.context)
                .setTitle(app.pachli.core.ui.R.string.title_hashtags_dialog)
                .setAdapter(
                    ArrayAdapter(
                        host.context,
                        android.R.layout.simple_list_item_1,
                        tags,
                    ),
                ) { _, which ->
                    statusActionListener.onViewTag(tags[which].toString())
                }
                .show()
                .let { forceFocus(it.listView) }
        }

        private fun getStatus(childView: View): T {
            return statusProvider.getStatus(recyclerView.getChildAdapterPosition(childView))!!
        }
    }

    private fun getLinks(status: IStatusViewData): Sequence<LinkSpanInfo> {
        val content = status.content
        return if (content is Spannable) {
            content.getSpans(0, content.length, URLSpan::class.java)
                .asSequence()
                .map { span ->
                    val text = content.subSequence(
                        content.getSpanStart(span),
                        content.getSpanEnd(span),
                    )
                    if (isHashtag(text)) null else LinkSpanInfo(text.toString(), span.url)
                }
                .filterNotNull()
        } else {
            emptySequence()
        }
    }

    private fun getHashtags(status: IStatusViewData): Sequence<CharSequence> {
        val content = status.content
        return content.getSpans(0, content.length, Object::class.java)
            .asSequence()
            .map { span ->
                content.subSequence(content.getSpanStart(span), content.getSpanEnd(span))
            }
            .filter(this::isHashtag)
    }

    private fun forceFocus(host: View) {
        interrupt()
        host.post {
            host.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
        }
    }

    private fun interrupt() {
        a11yManager.interrupt()
    }

    private fun isHashtag(text: CharSequence) = text.startsWith("#")

    private val collapseCwAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_collapse_cw,
        context.getString(R.string.post_content_warning_show_less),
    )

    private val expandCwAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_expand_cw,
        context.getString(R.string.post_content_warning_show_more),
    )

    private val replyAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_reply,
        context.getString(R.string.action_reply),
    )

    private val unreblogAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_unreblog,
        context.getString(R.string.action_unreblog),
    )

    private val reblogAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_reblog,
        context.getString(R.string.action_reblog),
    )

    private val unfavouriteAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_unfavourite,
        context.getString(R.string.action_unfavourite),
    )

    private val favouriteAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_favourite,
        context.getString(R.string.action_favourite),
    )

    private val bookmarkAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_bookmark,
        context.getString(R.string.action_bookmark),
    )

    private val unbookmarkAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_unbookmark,
        context.getString(R.string.action_bookmark),
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
        context.getString(R.string.status_filtered_show_anyway),
    )

    private val editFilterAction = AccessibilityActionCompat(
        app.pachli.core.ui.R.id.action_edit_filter,
        context.getString(R.string.filter_edit_title),
    )

    private data class LinkSpanInfo(val text: String, val link: String)
}

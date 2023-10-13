package app.pachli.adapter

import android.graphics.drawable.Drawable
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.view.View
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.entity.Status
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.CardViewMode
import app.pachli.util.NoUnderlineURLSpan
import app.pachli.util.StatusDisplayOptions
import app.pachli.util.createClickableText
import app.pachli.viewdata.StatusViewData
import java.text.DateFormat

class StatusDetailedViewHolder(view: View) : StatusBaseViewHolder(view) {
    private val reblogs: TextView
    private val favourites: TextView
    private val infoDivider: View

    init {
        reblogs = view.findViewById(R.id.status_reblogs)
        favourites = view.findViewById(R.id.status_favourites)
        infoDivider = view.findViewById(R.id.status_info_divider)
    }

    override fun setMetaData(
        statusViewData: StatusViewData,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener
    ) {
        val (_, _, _, _, _, _, _, createdAt, editedAt, _, _, _, _, _, _, _, _, _, visibility, _, _, _, app) = statusViewData.actionable
        val context = metaInfo.context
        val visibilityIcon = getVisibilityIcon(visibility)
        val visibilityString = getVisibilityDescription(context, visibility)
        val sb = SpannableStringBuilder(visibilityString)
        if (visibilityIcon != null) {
            val visibilityIconSpan = ImageSpan(
                visibilityIcon,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) DynamicDrawableSpan.ALIGN_CENTER else DynamicDrawableSpan.ALIGN_BASELINE
            )
            sb.setSpan(
                visibilityIconSpan,
                0,
                visibilityString.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        val metadataJoiner = context.getString(R.string.metadata_joiner)
        if (createdAt != null) {
            sb.append(" ")
            sb.append(dateFormat.format(createdAt))
        }
        if (editedAt != null) {
            val editedAtString =
                context.getString(R.string.post_edited, dateFormat.format(editedAt))
            sb.append(metadataJoiner)
            val spanStart = sb.length
            val spanEnd = spanStart + editedAtString.length
            sb.append(editedAtString)
            if (statusViewData.status.editedAt != null) {
                val editedClickSpan: NoUnderlineURLSpan = object : NoUnderlineURLSpan("") {
                    override fun onClick(view: View) {
                        listener.onShowEdits(bindingAdapterPosition)
                    }
                }
                sb.setSpan(editedClickSpan, spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        if (app != null) {
            sb.append(metadataJoiner)
            if (app.website != null) {
                val text = createClickableText(app.name, app.website)
                sb.append(text)
            } else {
                sb.append(app.name)
            }
        }
        metaInfo.movementMethod = LinkMovementMethod.getInstance()
        metaInfo.text = sb
    }

    private fun setReblogAndFavCount(
        reblogCount: Int,
        favCount: Int,
        listener: StatusActionListener
    ) {
        if (reblogCount > 0) {
            reblogs.text = getReblogsText(reblogs.context, reblogCount)
            reblogs.visibility = View.VISIBLE
        } else {
            reblogs.visibility = View.GONE
        }
        if (favCount > 0) {
            favourites.text = getFavsText(favourites.context, favCount)
            favourites.visibility = View.VISIBLE
        } else {
            favourites.visibility = View.GONE
        }
        if (reblogs.visibility == View.GONE && favourites.visibility == View.GONE) {
            infoDivider.visibility = View.GONE
        } else {
            infoDivider.visibility = View.VISIBLE
        }
        reblogs.setOnClickListener { v: View? ->
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onShowReblogs(position)
            }
        }
        favourites.setOnClickListener { v: View? ->
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onShowFavs(position)
            }
        }
    }

    override fun setupWithStatus(
        status: StatusViewData,
        listener: StatusActionListener,
        statusDisplayOptions: StatusDisplayOptions,
        payloads: Any?
    ) {
        // We never collapse statuses in the detail view
        val uncollapsedStatus =
            if (status.isCollapsible && status.isCollapsed) status.copyWithCollapsed(false) else status
        super.setupWithStatus(uncollapsedStatus, listener, statusDisplayOptions, payloads)
        setupCard(
            uncollapsedStatus,
            status.isExpanded,
            CardViewMode.FULL_WIDTH,
            statusDisplayOptions,
            listener
        ) // Always show card for detailed status
        if (payloads == null) {
            val (_, _, _, _, _, _, _, _, _, _, reblogsCount, favouritesCount) = uncollapsedStatus.actionable
            if (!statusDisplayOptions.hideStats) {
                setReblogAndFavCount(
                    reblogsCount,
                    favouritesCount, listener
                )
            } else {
                hideQuantitativeStats()
            }
        }
    }

    private fun getVisibilityIcon(visibility: Status.Visibility?): Drawable? {
        if (visibility == null) {
            return null
        }
        val visibilityIcon: Int
        visibilityIcon = when (visibility) {
            Status.Visibility.PUBLIC -> R.drawable.ic_public_24dp
            Status.Visibility.UNLISTED -> R.drawable.ic_lock_open_24dp
            Status.Visibility.PRIVATE -> R.drawable.ic_lock_outline_24dp
            Status.Visibility.DIRECT -> R.drawable.ic_email_24dp
            else -> {
                return null
            }
        }
        val visibilityDrawable = AppCompatResources.getDrawable(
            metaInfo.context, visibilityIcon
        ) ?: return null
        val size = metaInfo.textSize.toInt()
        visibilityDrawable.setBounds(
            0,
            0,
            size,
            size
        )
        visibilityDrawable.setTint(metaInfo.currentTextColor)
        return visibilityDrawable
    }

    private fun hideQuantitativeStats() {
        reblogs.visibility = View.GONE
        favourites.visibility = View.GONE
        infoDivider.visibility = View.GONE
    }

    companion object {
        private val dateFormat =
            DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.SHORT)
    }
}

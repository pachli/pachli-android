package app.pachli.adapter

import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import app.pachli.R
import app.pachli.databinding.ItemStatusDetailedBinding
import app.pachli.entity.description
import app.pachli.entity.icon
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.CardViewMode
import app.pachli.util.NoUnderlineURLSpan
import app.pachli.util.StatusDisplayOptions
import app.pachli.util.createClickableText
import app.pachli.util.hide
import app.pachli.util.show
import app.pachli.viewdata.StatusViewData
import java.text.DateFormat

class StatusDetailedViewHolder(
    private val binding: ItemStatusDetailedBinding
) : StatusBaseViewHolder(binding.root) {

    override fun setMetaData(
        statusViewData: StatusViewData,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener,
    ) {
        val (_, _, _, _, _, _, _, createdAt, editedAt, _, _, _, _, _, _, _, _, _, visibility, _, _, _, app) = statusViewData.actionable
        val context = metaInfo.context
        val visibilityIcon = visibility.icon(metaInfo)
        val visibilityString = visibility.description(context)
        val sb = SpannableStringBuilder(visibilityString)
        visibilityIcon?.let {
            val alignment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) DynamicDrawableSpan.ALIGN_CENTER else DynamicDrawableSpan.ALIGN_BASELINE
            val visibilityIconSpan = ImageSpan(it, alignment)
            sb.setSpan(visibilityIconSpan, 0, visibilityString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val metadataJoiner = context.getString(R.string.metadata_joiner)
        sb.append(" ")
        sb.append(dateFormat.format(createdAt))

        editedAt?.let {
            val editedAtString = context.getString(R.string.post_edited, dateFormat.format(it))
            sb.append(metadataJoiner)
            val spanStart = sb.length
            val spanEnd = spanStart + editedAtString.length
            sb.append(editedAtString)
            statusViewData.status.editedAt?.let {
                val editedClickSpan: NoUnderlineURLSpan = object : NoUnderlineURLSpan("") {
                    override fun onClick(view: View) {
                        listener.onShowEdits(bindingAdapterPosition)
                    }
                }
                sb.setSpan(editedClickSpan, spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        app?.let {
            val (name, website) = it
            sb.append(metadataJoiner)
            website?.let { sb.append(createClickableText(name, it)) ?: sb.append(name) }
        }

        metaInfo.movementMethod = LinkMovementMethod.getInstance()
        metaInfo.text = sb
    }

    private fun setReblogAndFavCount(
        reblogCount: Int,
        favCount: Int,
        listener: StatusActionListener,
    ) {
        if (reblogCount > 0) {
            binding.statusReblogs.text = getReblogsText(binding.statusReblogs.context, reblogCount)
            binding.statusReblogs.show()
        } else {
            binding.statusReblogs.hide()
        }
        if (favCount > 0) {
            binding.statusFavourites.text = getFavsText(binding.statusFavourites.context, favCount)
            binding.statusFavourites.show()
        } else {
            binding.statusFavourites.hide()
        }
        if (binding.statusReblogs.visibility == View.GONE && binding.statusFavourites.visibility == View.GONE) {
            binding.statusInfoDivider.hide()
        } else {
            binding.statusInfoDivider.show()
        }
        binding.statusReblogs.setOnClickListener {
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onShowReblogs(position)
            }
        }
        binding.statusFavourites.setOnClickListener {
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
        payloads: Any?,
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
            listener,
        ) // Always show card for detailed status
        if (payloads == null) {
            val (_, _, _, _, _, _, _, _, _, _, reblogsCount, favouritesCount) = uncollapsedStatus.actionable
            if (!statusDisplayOptions.hideStats) {
                setReblogAndFavCount(
                    reblogsCount,
                    favouritesCount,
                    listener,
                )
            } else {
                hideQuantitativeStats()
            }
        }
    }

    private fun hideQuantitativeStats() {
        binding.statusReblogs.hide()
        binding.statusFavourites.hide()
        binding.statusInfoDivider.hide()
    }

    companion object {
        private val dateFormat =
            DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.SHORT)
    }
}

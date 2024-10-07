package app.pachli.adapter

import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.view.View
import app.pachli.R
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.preferences.CardViewMode
import app.pachli.core.ui.NoUnderlineURLSpan
import app.pachli.core.ui.createClickableText
import app.pachli.databinding.ItemStatusDetailedBinding
import app.pachli.interfaces.StatusActionListener
import app.pachli.util.description
import app.pachli.util.icon
import app.pachli.viewdata.StatusViewData
import java.text.DateFormat
import java.util.Locale

class StatusDetailedViewHolder(
    pachliAccountId: Long,
    private val binding: ItemStatusDetailedBinding,
) : StatusBaseViewHolder<StatusViewData>(pachliAccountId, binding.root) {

    override fun setMetaData(
        viewData: StatusViewData,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener<StatusViewData>,
    ) {
        val (_, _, _, _, _, _, _, createdAt, editedAt, _, _, _, _, _, _, _, _, _, visibility, _, _, _, app) = viewData.actionable
        val visibilityIcon = visibility.icon(metaInfo)
        val visibilityString = visibility.description(context)
        val sb = SpannableStringBuilder(visibilityString)
        visibilityIcon?.also {
            val alignment = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) DynamicDrawableSpan.ALIGN_CENTER else DynamicDrawableSpan.ALIGN_BASELINE
            val visibilityIconSpan = ImageSpan(it, alignment)
            sb.setSpan(visibilityIconSpan, 0, visibilityString?.length ?: 0, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val metadataJoiner = context.getString(R.string.metadata_joiner)
        sb.append(" ")
        sb.append(dateFormat.format(createdAt))

        viewData.status.language?.also {
            sb.append(metadataJoiner)
            sb.append(it.uppercase(Locale.getDefault()))
        }

        editedAt?.also {
            val editedAtString = context.getString(R.string.post_edited, dateFormat.format(it))
            sb.append(metadataJoiner)
            val spanStart = sb.length
            val spanEnd = spanStart + editedAtString.length
            sb.append(editedAtString)
            viewData.status.editedAt?.also {
                val editedClickSpan: NoUnderlineURLSpan = object : NoUnderlineURLSpan("") {
                    override fun onClick(view: View) {
                        listener.onShowEdits(viewData.actionableId)
                    }
                }
                sb.setSpan(editedClickSpan, spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        app?.also { (name, website) ->
            sb.append(metadataJoiner)
            website?.also { sb.append(createClickableText(name, it)) } ?: sb.append(name)
        }

        metaInfo.movementMethod = LinkMovementMethod.getInstance()
        metaInfo.text = sb
    }

    private fun setReblogAndFavCount(
        viewData: StatusViewData,
        reblogCount: Int,
        favCount: Int,
        listener: StatusActionListener<StatusViewData>,
    ) {
        if (reblogCount > 0) {
            binding.statusReblogs.text = getReblogsCountDescription(reblogCount)
            binding.statusReblogs.show()
        } else {
            binding.statusReblogs.hide()
        }
        if (favCount > 0) {
            binding.statusFavourites.text = getFavouritesCountDescription(favCount)
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
            listener.onShowReblogs(viewData.actionableId)
        }
        binding.statusFavourites.setOnClickListener {
            listener.onShowFavs(viewData.actionableId)
        }
    }

    override fun setupWithStatus(
        viewData: StatusViewData,
        listener: StatusActionListener<StatusViewData>,
        statusDisplayOptions: StatusDisplayOptions,
        payloads: Any?,
    ) {
        // We never collapse statuses in the detail view
        val uncollapsedStatus =
            if (viewData.isCollapsible && viewData.isCollapsed) viewData.copy(isCollapsed = false) else viewData
        super.setupWithStatus(uncollapsedStatus, listener, statusDisplayOptions, payloads)
        setupCard(
            uncollapsedStatus,
            viewData.isExpanded,
            CardViewMode.FULL_WIDTH,
            statusDisplayOptions,
            listener,
        ) // Always show card for detailed status
        if (payloads == null) {
            val (_, _, _, _, _, _, _, _, _, _, reblogsCount, favouritesCount) = uncollapsedStatus.actionable
            if (!statusDisplayOptions.hideStats) {
                setReblogAndFavCount(viewData, reblogsCount, favouritesCount, listener)
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

package app.pachli.adapter

import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import androidx.core.view.isGone
import app.pachli.R
import app.pachli.core.activity.OpenUrlUseCase
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.data.model.StatusViewData
import app.pachli.core.preferences.CardViewMode
import app.pachli.core.ui.NoUnderlineURLSpan
import app.pachli.core.ui.SetStatusContent
import app.pachli.core.ui.StatusActionListener
import app.pachli.core.ui.createClickableText
import app.pachli.core.ui.extensions.description
import app.pachli.core.ui.extensions.icon
import app.pachli.databinding.ItemStatusDetailedBinding
import com.bumptech.glide.RequestManager
import java.text.DateFormat
import java.util.Locale

class StatusDetailedViewHolder(
    private val binding: ItemStatusDetailedBinding,
    glide: RequestManager,
    setStatusContent: SetStatusContent,
    private val openUrl: OpenUrlUseCase,
) : StatusBaseViewHolder<StatusViewData>(binding.root, glide, setStatusContent) {

    override fun setMetaData(
        viewData: StatusViewData,
        statusDisplayOptions: StatusDisplayOptions,
        listener: StatusActionListener<StatusViewData>,
    ) {
        val createdAt = viewData.actionable.createdAt
        val editedAt = viewData.actionable.editedAt
        val visibility = viewData.actionable.visibility
        val app = viewData.actionable.application

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
                val editedClickSpan = NoUnderlineURLSpan(viewData.actionableId, listener::onShowEdits)
                sb.setSpan(editedClickSpan, spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        app?.also { (name, website) ->
            sb.append(metadataJoiner)
            website?.also { sb.append(createClickableText(name, it, openUrl::invoke)) } ?: sb.append(name)
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
        if (binding.statusReblogs.isGone && binding.statusFavourites.isGone) {
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
        payloads: List<List<Any?>>?,
    ) {
        // We never collapse statuses in the detail view
        val uncollapsedViewdata =
            if (viewData.isCollapsible && viewData.isCollapsed) viewData.copy(isCollapsed = false) else viewData
        super.setupWithStatus(uncollapsedViewdata, listener, statusDisplayOptions, payloads)
        setupCard(
            uncollapsedViewdata,
            viewData.isExpanded,
            CardViewMode.FULL_WIDTH,
            statusDisplayOptions,
            listener,
        ) // Always show card for detailed status
        if (payloads.isNullOrEmpty()) {
            val reblogsCount = uncollapsedViewdata.actionable.reblogsCount
            val favouritesCount = uncollapsedViewdata.actionable.favouritesCount
            if (!statusDisplayOptions.hideStatsInDetailedView) {
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

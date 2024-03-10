package app.pachli.components.account.media

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.PaintDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import app.pachli.R
import app.pachli.adapter.isPlayable
import app.pachli.core.activity.decodeBlurHash
import app.pachli.core.common.extensions.show
import app.pachli.core.common.extensions.visible
import app.pachli.core.navigation.AttachmentViewData
import app.pachli.core.network.model.Attachment
import app.pachli.databinding.ItemAccountMediaBinding
import app.pachli.util.BindingHolder
import app.pachli.util.StatusDisplayOptions
import app.pachli.util.getFormattedDescription
import app.pachli.util.iconResource
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors

class AccountMediaGridAdapter(
    context: Context,
    statusDisplayOptions: StatusDisplayOptions,
    private val onAttachmentClickListener: (AttachmentViewData, View) -> Unit,
) : PagingDataAdapter<AttachmentViewData, BindingHolder<ItemAccountMediaBinding>>(
    object : DiffUtil.ItemCallback<AttachmentViewData>() {
        override fun areItemsTheSame(oldItem: AttachmentViewData, newItem: AttachmentViewData): Boolean {
            return oldItem.attachment.id == newItem.attachment.id
        }

        override fun areContentsTheSame(oldItem: AttachmentViewData, newItem: AttachmentViewData): Boolean {
            return oldItem == newItem
        }
    },
) {
    var statusDisplayOptions = statusDisplayOptions
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    private val playableIcon = AppCompatResources.getDrawable(context, R.drawable.ic_play_indicator)
    private val mediaHiddenDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_hide_media_24dp)

    val defaultSize = context.resources.getDimensionPixelSize(app.pachli.core.designsystem.R.dimen.account_media_grid_default)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemAccountMediaBinding> {
        val binding = ItemAccountMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemAccountMediaBinding>, position: Int) = with(holder.binding) {
        val item = getItem(position) ?: return

        val context = root.context

        when {
            item.sensitive && !item.isRevealed -> {
                overlay.show()
                overlay.setImageDrawable(mediaHiddenDrawable)
                overlay.setBackgroundResource(R.drawable.media_warning_bg)

                val (placeholder, width, height) = item.attachment.placeholder(context, preview)

                Glide.with(preview)
                    .load(placeholder)
                    .override(width, height)
                    .centerInside()
                    .into(preview)

                preview.contentDescription = context.getString(R.string.post_media_hidden_title)
            }

            item.attachment.isPreviewable() -> {
                if (item.attachment.type.isPlayable()) overlay.setImageDrawable(playableIcon)
                overlay.setBackgroundResource(0)
                overlay.visible(item.attachment.type.isPlayable())

                val (placeholder, _, _) = item.attachment.placeholder(context, preview)

                Glide.with(preview)
                    .asBitmap()
                    .load(item.attachment.previewUrl)
                    .placeholder(placeholder)
                    .into(preview)

                preview.contentDescription = item.attachment.getFormattedDescription(context)
            }

            else -> {
                if (item.attachment.type.isPlayable()) overlay.setImageDrawable(playableIcon)
                overlay.setBackgroundResource(0)
                overlay.visible(item.attachment.type.isPlayable())

                Glide.with(preview)
                    .load(item.attachment.iconResource())
                    .into(preview)

                preview.contentDescription = item.attachment.getFormattedDescription(context)
            }
        }

        root.setOnClickListener {
            onAttachmentClickListener(item, preview)
        }

        root.setOnLongClickListener { view ->
            val description = item.attachment.getFormattedDescription(view.context)
            Toast.makeText(context, description, Toast.LENGTH_LONG).show()
            true
        }
    }

    /**
     * Determine the placeholder for this [Attachment].
     *
     * @return A triple of the [Drawable] that should be used for the placeholder, and the
     *     width and height to set on the imageview displaying the placeholder.
     */
    fun Attachment.placeholder(context: Context, view: View): Triple<Drawable?, Int, Int> {
        //  To avoid the list jumping when the user taps the placeholder to reveal the media
        //  the placeholder must have the same size as the underlying preview.
        //
        // To do this take the height and width of the `small` image from the attachment
        // metadata. If height doesn't exist / is null try and compute it from the width and
        // the aspect ratio, falling back to 100 if both are missing.
        //
        // Do the same to compute the width.
        val height = when {
            meta?.small?.height != null -> meta?.small?.height!!
            meta?.small?.width != null && meta?.small?.aspect != null ->
                (meta?.small?.width!! / meta?.small?.aspect!!).toInt()
            else -> defaultSize
        }

        val width = when {
            meta?.small?.width != null -> meta?.small?.width!!
            meta?.small?.aspect != null -> (height * meta?.small?.aspect!!).toInt()
            else -> defaultSize
        }

        // The drawable's height and width does not need to be as large, as it will be
        // automatically scaled by Glide. Set to a max height of 32, and scale the width
        // appropriately.
        val placeholderHeight = 32
        val placeholderWidth = (placeholderHeight * (meta?.small?.aspect ?: 1.0)).toInt()

        val placeholder = if (statusDisplayOptions.useBlurhash) {
            blurhash?.let { decodeBlurHash(context, it, placeholderWidth, placeholderHeight) }
        } else {
            PaintDrawable(MaterialColors.getColor(view, android.R.attr.textColorLink)).apply {
                intrinsicHeight = placeholderHeight
                intrinsicWidth = placeholderWidth
            }
        }

        return Triple(placeholder, width, height)
    }
}

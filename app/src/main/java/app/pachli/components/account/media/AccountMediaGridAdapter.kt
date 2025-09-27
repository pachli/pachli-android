package app.pachli.components.account.media

import android.content.Context
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import app.pachli.core.common.extensions.hide
import app.pachli.core.common.extensions.show
import app.pachli.core.common.util.unsafeLazy
import app.pachli.core.data.model.StatusDisplayOptions
import app.pachli.core.model.Attachment
import app.pachli.core.model.AttachmentDisplayAction
import app.pachli.core.model.AttachmentDisplayReason
import app.pachli.core.navigation.AttachmentViewData
import app.pachli.core.ui.BindingHolder
import app.pachli.core.ui.extensions.getFormattedDescription
import app.pachli.databinding.ItemAccountMediaBinding
import com.bumptech.glide.RequestManager

class AccountMediaGridAdapter(
    context: Context,
    private val glide: RequestManager,
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

    /**
     * Default pixel size to use for width or height when previewing an attachment
     * if the attachment does not have metadata describing this.
     */
    private val defaultPreviewDimenPx by unsafeLazy {
        context.resources.getDimensionPixelSize(app.pachli.core.designsystem.R.dimen.account_media_grid_default)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemAccountMediaBinding> {
        val binding = ItemAccountMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemAccountMediaBinding>, position: Int) = with(holder.binding) {
        val item = getItem(position) ?: return

        val context = root.context
        val size = item.attachment.previewSize()

        when {
            item.sensitive && !item.isRevealed -> {
                overlay.show()
                preview.bind(
                    glide,
                    item.attachment,
                    AttachmentDisplayAction.Hide(
                        AttachmentDisplayReason.Sensitive,
                    ),
                    statusDisplayOptions.useBlurhash,
                    size,
                )
            }

            else -> {
                overlay.hide()
                preview.bind(
                    glide,
                    item.attachment,
                    AttachmentDisplayAction.Show(),
                    statusDisplayOptions.useBlurhash,
                    size,
                )
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
     * @return The size of the image to use when previewing this attachment, based on
     * [this.meta.small][Attachment.MetaData.small]. If no metadata for the small preview
     * is provided then falls back to [defaultPreviewDimenPx].
     */
    private fun Attachment.previewSize(): Size {
        //  To avoid the list jumping when the user taps the placeholder to reveal the media
        //  the placeholder must have the same size as the underlying preview.
        //
        // To do this take the height and width of the `small` image from the attachment
        // metadata. If height doesn't exist / is null try and compute it from the width and
        // the aspect ratio, falling back to 100 if both are missing.
        //
        // Do the same to compute the width.
        val small = meta?.small

        val height = when {
            small?.height != null -> small.height!!
            small?.width != null && small.aspect != null -> (small.width!! / small.aspect!!).toInt()
            else -> defaultPreviewDimenPx
        }

        val width = when {
            small?.width != null -> small.width!!
            small?.aspect != null -> (height * small.aspect!!).toInt()
            else -> defaultPreviewDimenPx
        }

        return Size(width, height)
    }
}

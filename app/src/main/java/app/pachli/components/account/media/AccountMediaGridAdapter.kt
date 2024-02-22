package app.pachli.components.account.media

import android.content.Context
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
import app.pachli.databinding.ItemAccountMediaBinding
import app.pachli.util.BindingHolder
import app.pachli.util.getFormattedDescription
import app.pachli.util.iconResource
import com.bumptech.glide.Glide

class AccountMediaGridAdapter(
    private val useBlurhash: Boolean,
    context: Context,
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

    private val playableIcon = AppCompatResources.getDrawable(context, R.drawable.ic_play_indicator)
    private val mediaHiddenDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_hide_media_24dp)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemAccountMediaBinding> {
        val binding = ItemAccountMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BindingHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingHolder<ItemAccountMediaBinding>, position: Int) = with(holder.binding) {
        val item = getItem(position) ?: return

        val context = root.context

        val placeholder = item.attachment.blurhash?.let {
            if (useBlurhash) decodeBlurHash(context, it) else null
        }

        when {
            item.sensitive && !item.isRevealed -> {
                overlay.show()
                overlay.setImageDrawable(mediaHiddenDrawable)

                Glide.with(preview)
                    .load(placeholder)
                    .centerInside()
                    .into(preview)

                preview.contentDescription = context.getString(R.string.post_media_hidden_title)
            }

            item.attachment.isPreviewable() -> {
                if (item.attachment.type.isPlayable()) overlay.setImageDrawable(playableIcon)
                overlay.visible(item.attachment.type.isPlayable())

                Glide.with(preview)
                    .asBitmap()
                    .load(item.attachment.previewUrl)
                    .placeholder(placeholder)
                    .into(preview)

                preview.contentDescription = item.attachment.getFormattedDescription(context)
            }

            else -> {
                if (item.attachment.type.isPlayable()) overlay.setImageDrawable(playableIcon)
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
}

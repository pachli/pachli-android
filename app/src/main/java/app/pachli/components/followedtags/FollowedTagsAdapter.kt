package app.pachli.components.followedtags

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import app.pachli.core.ui.BindingHolder
import app.pachli.databinding.ItemFollowedHashtagBinding
import app.pachli.interfaces.HashtagActionListener

class FollowedTagsAdapter(
    private val actionListener: HashtagActionListener,
    private val viewModel: FollowedTagsViewModel,
) : PagingDataAdapter<String, BindingHolder<ItemFollowedHashtagBinding>>(STRING_COMPARATOR) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemFollowedHashtagBinding> =
        BindingHolder(ItemFollowedHashtagBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: BindingHolder<ItemFollowedHashtagBinding>, position: Int) {
        viewModel.tags[position].let { tag ->
            with(holder.binding) {
                followedTag.text = tag.name
                followedTag.setOnClickListener {
                    actionListener.onViewTag(tag.name)
                }

                followedTagUnfollow.setOnClickListener {
                    actionListener.unfollow(tag.name, holder.bindingAdapterPosition)
                }
            }
        }
    }

    override fun getItemCount(): Int = viewModel.tags.size

    companion object {
        val STRING_COMPARATOR = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        }
    }
}

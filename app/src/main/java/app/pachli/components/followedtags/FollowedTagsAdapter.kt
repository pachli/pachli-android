package app.pachli.components.followedtags

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import app.pachli.core.network.model.HashTag
import app.pachli.core.ui.BindingHolder
import app.pachli.databinding.ItemFollowedHashtagBinding
import app.pachli.interfaces.HashtagActionListener

class FollowedTagsAdapter(
    private val actionListener: HashtagActionListener,
    private val viewModel: FollowedTagsViewModel,
) : PagingDataAdapter<HashTag, BindingHolder<ItemFollowedHashtagBinding>>(HashTagComparator) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemFollowedHashtagBinding> = BindingHolder(ItemFollowedHashtagBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: BindingHolder<ItemFollowedHashtagBinding>, position: Int) {
        getItem(position)?.let { tag ->
            with(holder.binding) {
                followedTag.text = tag.name
                followedTag.setOnClickListener {
                    actionListener.onViewTag(tag.name)
                }

                followedTagUnfollow.setOnClickListener {
                    actionListener.unfollow(tag.name)
                }
            }
        }
    }

    override fun getItemCount(): Int = viewModel.tags.size

    companion object {
        val HashTagComparator = object : DiffUtil.ItemCallback<HashTag>() {
            override fun areItemsTheSame(oldItem: HashTag, newItem: HashTag): Boolean = oldItem == newItem
            override fun areContentsTheSame(oldItem: HashTag, newItem: HashTag): Boolean = oldItem == newItem
        }
    }
}

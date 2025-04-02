package app.pachli.components.followedtags

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import app.pachli.R
import app.pachli.core.network.model.HashTag
import app.pachli.core.ui.BindingHolder
import app.pachli.databinding.ItemFollowedHashtagBinding
import app.pachli.interfaces.HashtagActionListener

class FollowedTagsAdapter(
    private val actionListener: HashtagActionListener,
) : PagingDataAdapter<HashTag, BindingHolder<ItemFollowedHashtagBinding>>(HashTagComparator) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemFollowedHashtagBinding> = BindingHolder(ItemFollowedHashtagBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: BindingHolder<ItemFollowedHashtagBinding>, position: Int) {
        getItem(position)?.let { tag ->
            with(holder.binding) {
                followedTag.text = tag.name
                followedTag.setOnClickListener {
                    actionListener.onViewTag(tag.name)
                }

                val usage = tag.history.sumOf { it.uses }
                val accounts = tag.history.sumOf { it.accounts }

                tagStats.text = root.resources.getString(
                    R.string.followed_hashtags_summary_fmt,
                    root.resources.getQuantityString(R.plurals.followed_hashtags_posts_count_fmt, usage, usage),
                    root.resources.getQuantityString(R.plurals.followed_hashtags_accounts_count_fmt, accounts, accounts),
                )

                followedTagUnfollow.setOnClickListener {
                    actionListener.unfollow(tag.name)
                }
            }
        }
    }

    companion object {
        val HashTagComparator = object : DiffUtil.ItemCallback<HashTag>() {
            override fun areItemsTheSame(oldItem: HashTag, newItem: HashTag): Boolean = oldItem == newItem
            override fun areContentsTheSame(oldItem: HashTag, newItem: HashTag): Boolean = oldItem == newItem
        }
    }
}

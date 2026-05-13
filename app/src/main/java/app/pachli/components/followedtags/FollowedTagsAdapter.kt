package app.pachli.components.followedtags

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import app.pachli.R
import app.pachli.core.model.Hashtag
import app.pachli.core.ui.BindingHolder
import app.pachli.databinding.ItemFollowedHashtagBinding

class FollowedTagsAdapter(
    private val onUnfollowTag: (tagName: String) -> Unit,
    private val onViewTag: (tagName: String) -> Unit,
) : ListAdapter<Hashtag, BindingHolder<ItemFollowedHashtagBinding>>(HashtagComparator) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingHolder<ItemFollowedHashtagBinding> = BindingHolder(ItemFollowedHashtagBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: BindingHolder<ItemFollowedHashtagBinding>, position: Int) {
        getItem(position)?.let { tag ->
            with(holder.binding) {
                followedTag.text = tag.name
                root.setOnClickListener { onViewTag(tag.name) }

                val usage = tag.history.sumOf { it.uses }
                val accounts = tag.history.sumOf { it.accounts }
                val days = tag.history.size

                tagStats.text = root.resources.getString(
                    R.string.followed_hashtags_summary_fmt,
                    root.resources.getQuantityString(R.plurals.followed_hashtags_posts_count_fmt, usage, usage),
                    root.resources.getQuantityString(R.plurals.followed_hashtags_accounts_count_fmt, accounts, accounts),
                    root.resources.getQuantityString(R.plurals.followed_hashtags_days_count_fmt, days, days),
                )

                followedTagUnfollow.setOnClickListener { onUnfollowTag(tag.name) }
            }
        }
    }

    companion object {
        val HashtagComparator = object : DiffUtil.ItemCallback<Hashtag>() {
            override fun areItemsTheSame(oldItem: Hashtag, newItem: Hashtag): Boolean = oldItem == newItem
            override fun areContentsTheSame(oldItem: Hashtag, newItem: Hashtag): Boolean = oldItem == newItem
        }
    }
}

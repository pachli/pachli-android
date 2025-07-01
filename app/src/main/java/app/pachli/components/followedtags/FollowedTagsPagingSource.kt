package app.pachli.components.followedtags

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.pachli.core.network.model.HashTag
import java.text.Collator

class FollowedTagsPagingSource(private val viewModel: FollowedTagsViewModel) : PagingSource<String, HashTag>() {
    override fun getRefreshKey(state: PagingState<String, HashTag>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, HashTag> {
        return if (params is LoadParams.Refresh) {
            LoadResult.Page(viewModel.tags.sortedWith(comparebyName), null, viewModel.nextKey)
        } else {
            LoadResult.Page(emptyList(), null, null)
        }
    }

    companion object {
        /** Locale aware collator by text. */
        private val text: Collator = Collator.getInstance().apply { strength = Collator.SECONDARY }

        /**
         * Locale-aware comparator for [HashTag]s. Case-insenstive comparison by
         * [HashTag.name].
         */
        val comparebyName: Comparator<HashTag> = compareBy(text) { it.name }
    }
}
